package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.DemandContract;
import com.example.contract.ProjectContract;
import com.example.state.DemandState;
import com.example.state.ProjectState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
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

            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);

            final StateAndRef<DemandState> demandStateUpd = getDemandStateByLinearId(linearId);
            final DemandState currentDemandState = demandStateUpd.getState().getData();

            // Stage 2. Resolve the lender and borrower identity if the obligation is anonymous.
            final Party platformLead = currentDemandState.getPlatformLead();
            final Party sponsor = currentDemandState.getSponsor();
            final PublicKey lenderKey = sponsor.getOwningKey();
            final PublicKey borrwerKey = platformLead.getOwningKey();

            CordaX500Name cioName = new CordaX500Name("CIO", "Singapore", "SG");
            CordaX500Name cooName = new CordaX500Name("COO", "Singapore", "SG");
            CordaX500Name dl1Name = new CordaX500Name("DLTeam1", "Singapore", "SG");


            final Party cio = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cioName);
            final Party coo = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cooName);
            final Party dl1 = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(dl1Name);

            final PublicKey cioKey = cio.getOwningKey();
            final PublicKey cooKey = coo.getOwningKey();

            // Stage 3. This flow can only be initiated by the current recipient.
            if (!platformLead.equals(initiatorParty)) {
                throw new FlowException("Update demand flow must be initiated by the platform lead.");
            }

            final DemandState newUpdatedDemand = currentDemandState.updateState(this.amount, this.startDate, this.endDate, Arrays.asList(cio,coo), linearId);
            final Command<DemandContract.Commands.Update> txCommandUpdate = new Command<>(new DemandContract.Commands.Update(), Arrays.asList(lenderKey, borrwerKey));
            final Command<ProjectContract.Commands.Create> txCommandApprove = new Command<>(new ProjectContract.Commands.Create(), Arrays.asList(cioKey, cooKey));


            // Stage 5
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            ProjectState projectState = new ProjectState("P1001", "A1001"
                    , newUpdatedDemand.getDescription()
                    ,newUpdatedDemand.getAmount()
                    , newUpdatedDemand.getStartDate(), newUpdatedDemand.getEndDate(),
                    newUpdatedDemand.getSponsor(), newUpdatedDemand.getPlatformLead(),cio, coo,dl1, currentDemandState.getLinearId().getId().toString());

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(demandStateUpd)
                    .addOutputState(newUpdatedDemand, DEMAND_CONTRACT_ID)
                    .addOutputState(projectState,PROJECT_CONTRACT_ID)
                    .addCommand(txCommandUpdate).addCommand(txCommandApprove);
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder, borrwerKey);

            FlowSession cioSession = initiateFlow(coo);
            FlowSession cooSession = initiateFlow(cio);
            FlowSession lenderIdentitySession = initiateFlow(sponsor);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(cioSession, cooSession, lenderIdentitySession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
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
