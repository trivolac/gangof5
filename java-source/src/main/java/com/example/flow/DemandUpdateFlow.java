package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.DemandContract;
import com.example.schema.DemandSchema;
import com.example.schema.DemandSchemaV1;
import com.example.state.DemandState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.finance.contracts.asset.Obligation;

import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.contract.DemandContract.DEMAND_CONTRACT_ID;

public class DemandUpdateFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction>{
        public static final String OBLIGATION_CONTRACT_ID = "net.corda.examples.obligation.ObligationContract";
        private final String description;
        private final Date startDate;
        private final Date endDate;
        private final int amount;
        private final UniqueIdentifier linearId;
        private final Party newLender;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            @Override public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
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

        public Initiator(UniqueIdentifier linearId, Party newLender, String description, Date startDate, Date endDate, int amount) {
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate;
            this.amount = amount;
            this.linearId = linearId;
            this.newLender = newLender;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        Party resolveIdentity(AbstractParty abstractParty) {
            return getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(abstractParty);
        }

        static class SignTxFlowNoChecking extends SignTransactionFlow {
            SignTxFlowNoChecking(FlowSession otherFlow, ProgressTracker progressTracker) {
                super(otherFlow, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction tx) {
                // TODO: Add checking here.
            }
        }
        StateAndRef<DemandState> getObligationByLinearId(UniqueIdentifier linearId) throws FlowException {
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED,
                    null);

            List<StateAndRef<DemandState>> obligations = getServiceHub().getVaultService().queryBy(DemandState.class, queryCriteria).getStates();
            if (obligations.size() != 1) {
                throw new FlowException(String.format("Obligation with id %s not found.", linearId));
            }
            return obligations.get(0);
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final Party initiatorParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);

            final StateAndRef<DemandState> demandStateUpd = getObligationByLinearId(linearId);
            final DemandState currentDemandState = demandStateUpd.getState().getData();

            // Stage 2. Resolve the lender and borrower identity if the obligation is anonymous.
            final Party borrowerIdentity = resolveIdentity(currentDemandState.getBorrower());
            final Party lenderIdentity = resolveIdentity(currentDemandState.getLender());

            // Stage 3. This flow can only be initiated by the current recipient.
            if (!borrowerIdentity.equals(getOurIdentity())) {
                throw new FlowException("Update Demand flow must be initiated by the borrower.");
            }

            // Stage 4. Create a update command.
            final List<PublicKey> requiredSigners = currentDemandState.getParticipantKeys();

            final DemandState updateDemand = currentDemandState.updateState(this.description, lenderIdentity, borrowerIdentity, this.amount, this.startDate, this.endDate );

            //  DemandState demandState = new DemandState(description, initiatorParty, newLender);
            final Command<DemandContract.Commands.Update> txCommand = new Command<>(new DemandContract.Commands.Update(), requiredSigners);

            // Stage 5
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);


            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(demandStateUpd)
                    .addOutputState(updateDemand, OBLIGATION_CONTRACT_ID)
                    .addCommand(txCommand);


            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            FlowSession otherPartySession = initiateFlow(lenderIdentity);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

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
            subFlow(new IdentitySyncFlow.Receive(otherPartyFlowSession));
            SignedTransaction stx = subFlow(new Initiator.SignTxFlowNoChecking(otherPartyFlowSession, SignTransactionFlow.Companion.tracker()));
            return waitForLedgerCommit(stx.getId());
        }
    }
}
