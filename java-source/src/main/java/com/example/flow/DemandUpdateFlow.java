package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.DemandContract;
import com.example.contract.ProjectContract;
import com.example.state.DemandState;
import com.example.state.ProjectState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.sourceforge.plantuml.project.Project;

import javax.ws.rs.core.Response;
import java.security.PublicKey;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;


public class DemandUpdateFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction>{
        public static final String DEMAND_CONTRACT_ID = "com.example.contract.DemandContract";
        public static final String PROJECT_CONTRACT_ID = "com.example.contract.ProjectContract";

        private final String startDate;
        private final String endDate;
        private final int amount;
        private final UniqueIdentifier linearId;


        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.");
        private final ProgressTracker.Step SYNCING = new ProgressTracker.Step("Syncing identities.")
        {
            @Override public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private Date getDate(String date){
            DateFormat df = new SimpleDateFormat("dd/MM/yyyy");

            try {
                return df.parse(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return null;
        }
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

        public Initiator(UniqueIdentifier linearId, String startDate, String endDate, int amount) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.amount = amount;
            this.linearId = linearId;
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
            final Party borrowerIdentity = currentDemandState.getBorrower();
            final Party lenderIdentity = currentDemandState.getLender();
            final PublicKey lenderKey = lenderIdentity.getOwningKey();
            final PublicKey borrwerKey = borrowerIdentity.getOwningKey();

            CordaX500Name cioName = new CordaX500Name("CIO", "Singapore", "SG");
            CordaX500Name cooName = new CordaX500Name("COO", "Singapore", "SG");
            CordaX500Name dl_1Name = new CordaX500Name("DLTeam1", "Singapore", "SG");


            final Party cio = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cioName);
            final Party coo = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(cooName);
            final Party dl_1 = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(dl_1Name);

            final PublicKey cioKey = cio.getOwningKey();
            final PublicKey cooKey = coo.getOwningKey();

            System.out.println("Patry CIO ::::: " + cio);
            System.out.println("Patry COO ::::: " + coo);

            // Stage 3. This flow can only be initiated by the current recipient.
            if (!borrowerIdentity.equals(getOurIdentity())) {
                throw new FlowException("Update Demand flow must be initiated by the borrower.");
            }

            // Stage 4. Create a update command.
            /*final List<PublicKey> requiredSigners = currentDemandState.getParticipantKeys();

            System.out.println("REquired signers ::::: " + requiredSigners);
*/
            final DemandState newUpdatedDemand = currentDemandState.updateState(this.amount, this.startDate, this.endDate, Arrays.asList(cio,coo), linearId);

            //  DemandState demandState = new DemandState(description, initiatorParty, newLender);
            final Command<DemandContract.Commands.Update> txCommandUpdate = new Command<>(new DemandContract.Commands.Update(), Arrays.asList(lenderKey, borrwerKey));

            //  DemandState demandState = new DemandState(description, initiatorParty, newLender);
            final Command<ProjectContract.Commands.Create> txCommandApprove = new Command<>(new ProjectContract.Commands.Create(), Arrays.asList(cioKey, cooKey));


            // Stage 5
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);

            ProjectState projectState = new ProjectState("P1001", "A1001", newUpdatedDemand.getDescription(),newUpdatedDemand.getAmount(), newUpdatedDemand.getStartDate(), newUpdatedDemand.getEndDate(),
                    newUpdatedDemand.getLender(), newUpdatedDemand.getBorrower(),cio, coo,dl_1);

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(demandStateUpd)
                    .addOutputState(newUpdatedDemand, DEMAND_CONTRACT_ID)
                    .addOutputState(projectState,PROJECT_CONTRACT_ID)
                    .addCommand(txCommandUpdate).addCommand(txCommandApprove);

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder, borrwerKey);
            System.out.println("SignedTransaction ::::::::::: " + partSignedTx);


            //FlowSession otherPartySession = initiateFlow(lenderIdentity);

            //FlowSession borrowerIdentitySession = initiateFlow(borrowerIdentity);
            FlowSession cioSession = initiateFlow(coo);
            FlowSession cooSession = initiateFlow(cio);
            FlowSession lenderIdentitySession = initiateFlow(lenderIdentity);

            // Stage 8. Send any keys and certificates so the signers can verify each other's identity.
         //   progressTracker.setCurrentStep(SYNCING);
            //final Set<FlowSession> sessions = ImmutableSet.of(initiateFlow(borrowerIdentity), initiateFlow(cio), initiateFlow(coo), initiateFlow(lenderIdentity));
           // subFlow(new IdentitySyncFlow.Send(sessions, partSignedTx.getTx(), SYNCING.childProgressTracker()));

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(cioSession, cooSession, lenderIdentitySession), CollectSignaturesFlow.Companion.tracker()));

            System.out.println("Fully SignedTransaction ::::::::::: " + fullySignedTx);


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
                    System.out.println("****************** SignTxFlow.checkTransaction ::::::::::::::::::::: " + otherPartyFlowSession.getCounterparty().getName());
                }
            }
            //subFlow(new IdentitySyncFlow.Receive(otherPartyFlowSession));
            SignedTransaction stx = subFlow(new SignTxFlow(otherPartyFlowSession, SignTransactionFlow.Companion.tracker()));
            System.out.println("Acceptor.call ::: SignedTransaction :::: "+ stx);


            return stx;

            //return waitForLedgerCommit(stx.getId());
        }



    }


}
