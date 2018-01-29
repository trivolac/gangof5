package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.AllocationContract;
import com.example.contract.ProjectContract;
import com.example.state.AllocationState;
import com.example.state.ProjectState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.example.contract.AllocationContract.ALLOCATION_CONTRACT_ID;
import static com.example.contract.ProjectContract.PROJECT_CONTRACT_ID;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class AllocationFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction>{
        private final UniqueIdentifier projectLinearId;
        private final Party deliveryTeam;
        private final int amount;
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;

        //steps
        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.");
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        //progress tracker
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(UniqueIdentifier projectLinearId, Party deliveryTeam, int amount, LocalDateTime startDate, LocalDateTime endDate) {
            this.projectLinearId = projectLinearId;
            this.deliveryTeam = deliveryTeam;
            this.amount = amount;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final Party initiatorParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            // Stage 1. Retrieve project specified by linearId from the vault.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final StateAndRef<ProjectState> inputProjectStateAndRef = getProjectStateByLinearId(projectLinearId);
            final ProjectState inputProjectState = inputProjectStateAndRef.getState().getData();

            // Stage 2. Resolve the project lead and coo identity
            final Party platformLead = inputProjectState.getPlatformLead();
            final Party coo = inputProjectState.getCoo();

            // Stage 3. This flow can only be initiated by the project lead.
            if(!platformLead.equals(initiatorParty)){
                throw new FlowException("Allocation flow must be initiated by the project lead.");
            }

            // Stage 4. Create Allocation Create Command and Project UpdateBudget Command
            final Command<AllocationContract.Commands.Create> allocationCreateCmd = new Command<>(
                    new AllocationContract.Commands.Create(),
                    Arrays.asList(platformLead.getOwningKey(), deliveryTeam.getOwningKey()));
            final Command<ProjectContract.Commands.UpdateBudget> projectUpdateCmd = new Command<>(
                    new ProjectContract.Commands.UpdateBudget(),
                    Arrays.asList(platformLead.getOwningKey(), coo.getOwningKey()));

            // Stage 5.Create output states
            final AllocationState outputAllocationState = new AllocationState(inputProjectState.getProjectCode(),
                    inputProjectState.getAllocationKey(), inputProjectState.getDescription(), platformLead, deliveryTeam,
                    coo, amount, startDate, endDate);
            final ProjectState outputProjectState = inputProjectState.deductAllocationFromBudget(amount);

            // Stage 6. Validate output allocation state against other allocations so that similar DL Team
            // should not have overlap allocation dates
            validateDLTeamAllocationDates(outputAllocationState);

            // Stage 6. Create transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputProjectStateAndRef)
                    .addOutputState(outputAllocationState, ALLOCATION_CONTRACT_ID)
                    .addOutputState(outputProjectState, PROJECT_CONTRACT_ID)
                    .addCommand(allocationCreateCmd)
                    .addCommand(projectUpdateCmd);


            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Stage 7. Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());


            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Stage 8. Sign the transaction
            final SignedTransaction partSignedTx = getServiceHub()
                    .signInitialTransaction(txBuilder,initiatorParty.getOwningKey());

            // Stage 9. Initiate Delivery Team's signature flow
           FlowSession deliveryTeamFlowSession = initiateFlow(deliveryTeam);
           FlowSession cooFlowSession = initiateFlow(coo);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Stage 10. Gather signature from delivery team
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(deliveryTeamFlowSession, cooFlowSession),
                            CollectSignaturesFlow.Companion.tracker()));


            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Stage 11. Notarise and record the transaction in all involved parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
        }

        private StateAndRef<ProjectState> getProjectStateByLinearId(UniqueIdentifier linearId) throws FlowException {
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED,
                    null);

            List<StateAndRef<ProjectState>> projects = getServiceHub().getVaultService().queryBy(ProjectState.class, queryCriteria).getStates();

            if (projects.size() != 1) {
                throw new FlowException(String.format("Project with id %s not found.", linearId));
            }
            return projects.get(0);
        }

        private void validateDLTeamAllocationDates(AllocationState newAllocationState) throws FlowException {
            final Party dlTeam = newAllocationState.getDeliveryTeam();
            final LocalDateTime startDate = newAllocationState.getStartDate();
            final LocalDateTime endDate = newAllocationState.getEndDate();
            final String projectCode = newAllocationState.getProjectCode();

            //retrieve all allocations with DL Team
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    ImmutableList.of(dlTeam),
                    null,
                    Vault.StateStatus.UNCONSUMED,
                    null);
            List<StateAndRef<AllocationState>> allocations = getServiceHub().getVaultService().queryBy(AllocationState.class, queryCriteria).getStates();

            //loop through allocations of DL team
            for(StateAndRef<AllocationState> allocationStateStateAndRef : allocations){
                AllocationState allocationState = allocationStateStateAndRef.getState().getData();

                //validate allocation dates if DL teams and project code are equal
                if(allocationState.getDeliveryTeam().equals(dlTeam) &&
                        allocationState.getProjectCode().equals(projectCode)){
                    LocalDateTime startDateToCheck = allocationState.getStartDate();
                    LocalDateTime endDateToCheck = allocationState.getEndDate();

                    //check overlap of start and end date
                    LocalDateTime maxStartDate = (startDate.isAfter(startDateToCheck)) ? startDate : startDateToCheck;
                    LocalDateTime minEndDate = (endDate.isBefore(endDateToCheck)) ? endDate : endDateToCheck;
                    if(maxStartDate.isBefore(minEndDate) || maxStartDate.isEqual(minEndDate)){
                        throw new FlowException("Dates specified must not overlap with existing allocation for delivery team.");
                    }
                }
            }
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction>{

        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow{
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker){
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an Allocation transaction.", output instanceof AllocationState);
//                        AllocationState allocationState = (AllocationState) output;

                        //add delivery team validation here

                        return null;
                    });
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }
}
