package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.DemandContract;
import com.example.contract.ProjectContract;
import com.example.state.DemandState;
import com.example.state.ProjectState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.contract.DemandContract.DEMAND_CONTRACT_ID;
import static com.example.contract.ProjectContract.PROJECT_CONTRACT_ID;
import static net.corda.core.contracts.ContractsDSL.requireThat;


public class DemandUpdateFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction>{
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final int amount;
        private final UniqueIdentifier linearId;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.");
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(UniqueIdentifier linearId, LocalDateTime startDate, LocalDateTime endDate, int amount) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.amount = amount;
            this.linearId = linearId;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        private StateAndRef<DemandState> getDemandStateByLinearId(UniqueIdentifier linearId) throws FlowException {
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED,
                    null);

            List<StateAndRef<DemandState>> demands = getServiceHub().getVaultService().queryBy(DemandState.class, queryCriteria).getStates();
            if (demands.size() != 1) {
                throw new FlowException(String.format("Demand with id %s not found.", linearId));
            }
            return demands.get(0);
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final Party initiatorParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            // Stage 1. Retrieve Demand specified by linearId from the vault.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);

            final StateAndRef<DemandState> demandStateUpd = getDemandStateByLinearId(linearId);
            final DemandState currentDemandState = demandStateUpd.getState().getData();

            // Stage 2. Resolve sponsor and platform lead identity
            final Party platformLead = currentDemandState.getPlatformLead();
            final Party sponsor = currentDemandState.getSponsor();
            final PublicKey sponsorKey = sponsor.getOwningKey();
            final PublicKey platformLeadKey = platformLead.getOwningKey();

            CordaX500Name cioName = new CordaX500Name("CIO", "Singapore", "SG");
            CordaX500Name cooName = new CordaX500Name("COO", "Singapore", "SG");
//            CordaX500Name dl1Name = new CordaX500Name("DLTeam1", "Singapore", "SG");


            final Party cio = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cioName);
            final Party coo = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cooName);
//            final Party dl1 = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(dl1Name);

            final PublicKey cioKey = cio.getOwningKey();
            final PublicKey cooKey = coo.getOwningKey();

            // Stage 3. This flow can only be initiated by the current recipient.
            if (!platformLead.equals(initiatorParty)) {
                throw new FlowException("Update demand flow must be initiated by the platform lead.");
            }

            //Stage 4. Generate commands
            final DemandState newUpdatedDemand = currentDemandState.updateState(this.amount, this.startDate, this.endDate, Arrays.asList(cio,coo), linearId);
            final Command<DemandContract.Commands.Update> txCommandUpdate = new Command<>(new DemandContract.Commands.Update(), Arrays.asList(sponsorKey, platformLeadKey));
            final Command<ProjectContract.Commands.Create> txCommandApprove = new Command<>(new ProjectContract.Commands.Create(), Arrays.asList(cioKey, cooKey));

            //Stage 5. Build transaction
            final String projectCode = generateProjectCode(initiatorParty);
            final String allocationKey = generateAllocationKey(initiatorParty);

            final ProjectState projectState = new ProjectState(projectCode, allocationKey
                    , newUpdatedDemand.getDescription()
                    ,newUpdatedDemand.getAmount()
                    , newUpdatedDemand.getStartDate(), newUpdatedDemand.getEndDate(),
                    newUpdatedDemand.getSponsor(), newUpdatedDemand.getPlatformLead(),cio, coo, currentDemandState.getLinearId().getId().toString());

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(demandStateUpd)
                    .addOutputState(newUpdatedDemand, DEMAND_CONTRACT_ID)
                    .addOutputState(projectState,PROJECT_CONTRACT_ID)
                    .addCommand(txCommandUpdate).addCommand(txCommandApprove);

            // Stage 6. Verify that the transaction is valid.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            // Stage 7. Sign the transaction
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder, platformLeadKey);

            // Stage 8. Send the state to the counterparty, and receive it back with their signature.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            FlowSession cioSession = initiateFlow(coo);
            FlowSession cooSession = initiateFlow(cio);
            FlowSession sponsorSession = initiateFlow(sponsor);
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(cioSession, cooSession, sponsorSession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 9. Notarise and record the transaction in all parties' vaults.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(fullySignedTx));
        }

        private String generateProjectCode(Party initiatingPlatformLead){
            List<String> projectCodes = retrieveAllProjectsCodesWithPlatformLead(initiatingPlatformLead);
            int index = projectCodes.size() + 1;

            StringBuilder sb = new StringBuilder();
            sb.append("P");
            sb.append(initiatingPlatformLead.getName().getOrganisation().substring(2,3));
            for(int i=5; i > (index/10); i--){
                sb.append(0);
            }
            sb.append(index);
            return sb.toString();
        }

        private String generateAllocationKey(Party initiatingPlatformLead){
            List<String> projectCodes = retrieveAllProjectsCodesWithPlatformLead(initiatingPlatformLead);
            int index = projectCodes.size() + 1;

            StringBuilder sb = new StringBuilder();
            sb.append("A");
            sb.append(initiatingPlatformLead.getName().getOrganisation().substring(2,3));
            for(int i=5; i > (index/10); i--){
                sb.append(0);
            }
            sb.append(index);
            return sb.toString();
        }

        private List<String> retrieveAllProjectsCodesWithPlatformLead(Party platformLead){
            //retrieve all projects with DL Team
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    ImmutableList.of(platformLead),
                    null,
                    Vault.StateStatus.ALL,
                    null);
            List<StateAndRef<ProjectState>> projects = getServiceHub().getVaultService().queryBy(ProjectState.class, queryCriteria).getStates();

            //return list of project codes
            return projects.stream()
                    .map(StateAndRef::getState)
                    .map(TransactionState::getData)
                    .map(ProjectState::getProjectCode)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction>{

        private final FlowSession otherPartyFlowSession;

        public Acceptor(FlowSession otherPartyFlowSession) {
            this.otherPartyFlowSession = otherPartyFlowSession;

        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    String party = getServiceHub().getMyInfo().getLegalIdentities().get(0).getName().getOrganisation();
                    if ("COO".equals(party)) {
                        requireThat(require -> {
                            ContractState outputContractState = stx.getTx().getOutputs().get(1).getData();
                            require.using("This must be a Project creation flow", outputContractState instanceof ProjectState);
                            ProjectState outputProjectState = (ProjectState) outputContractState;
                            require.using("COO is not accepting Projects with initial ask of >500k", outputProjectState.getBudget() <= 500000);
                            return null;
                        });
                    }
                    if("CIO".equals(party)) {
                        requireThat(require -> {
                            ContractState outputContractState = stx.getTx().getOutputs().get(1).getData();
                            require.using("This must be a Project creation flow", outputContractState instanceof ProjectState);
                            ProjectState outputProjectState = (ProjectState) outputContractState;

                            LocalDateTime startDate = outputProjectState.getStartDate();
                            LocalDateTime endDate = outputProjectState.getEndDate();

                            //difference
                            long year = startDate.until(endDate, ChronoUnit.YEARS);
                            long month = startDate.until(endDate, ChronoUnit.MONTHS);
                            long day = startDate.until(endDate, ChronoUnit.DAYS);
                            require.using("CIO is not allowing Projects with duration more than 2yrs.", year < 2 || (year == 2 && month == 0 && day == 0));
                            return null;
                        });

                    }
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlowSession, SignTransactionFlow.Companion.tracker()));
        }



    }


}
