package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.DemandContract;
import com.example.state.DemandState;
import com.google.common.collect.Sets;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.contract.DemandContract.DEMAND_CONTRACT_ID;

public class DemandCreationFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction>{

        private final String description;
        private final Party platformLead;

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

        public Initiator(String description, Party platformLead) {
            this.description = description;
            this.platformLead = platformLead;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final Party initiatorParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            DemandState demandState = new DemandState(description, initiatorParty, platformLead);
            final Command<DemandContract.Commands.Create> txCommand = new Command<>(new DemandContract.Commands.Create(),
                    demandState.getParticipants().stream().filter(Objects::nonNull).map(AbstractParty::getOwningKey).collect(Collectors.toList()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary).withItems(new StateAndContract(demandState, DEMAND_CONTRACT_ID), txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            FlowSession otherPartySession = initiateFlow(platformLead);

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
            class SignTxFlow extends SignTransactionFlow {

                private SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    //add validation for platform lead specific checks
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlowSession, SignTransactionFlow.Companion.tracker()));
        }
    }
}
