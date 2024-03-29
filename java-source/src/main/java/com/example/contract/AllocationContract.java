package com.example.contract;

import com.example.state.AllocationState;
import com.example.state.ProjectState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Arrays;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class AllocationContract implements Contract {
    public static final String ALLOCATION_CONTRACT_ID = "com.example.contract.AllocationContract";
    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        if(command.getValue() instanceof Commands.Create){
            requireThat(require -> {
                require.using("Only 1 input should be consumed when creating an allocation.",
                        tx.getInputs().size() == 1);
                require.using("Two output state should be created.",
                        tx.getOutputs().size() == 2);

                final ProjectState inputProjectState = tx.inputsOfType(ProjectState.class).get(0);
                final AllocationState outputAllocationState = tx.outputsOfType(AllocationState.class).get(0);
                final ProjectState outputProjectState = tx.outputsOfType(ProjectState.class).get(0);

                //checking output allocation state
                require.using("Description must exist.",
                        !outputAllocationState.getDescription().isEmpty());
                require.using("Description must tally between input and output",
                        inputProjectState.getDescription().equals(outputAllocationState.getDescription()));
                require.using("Platform Lead must exist.",
                        outputAllocationState.getPlatformLead() != null);
                require.using("Platform Lead must tally between input and output",
                        inputProjectState.getPlatformLead().equals(outputAllocationState.getPlatformLead()));
                require.using("Delivery Team must exist.",
                        outputAllocationState.getDeliveryTeam() != null);
                require.using("COO must exist",
                        outputAllocationState.getCoo() != null);
                require.using("COO must tally between input and output",
                        inputProjectState.getCoo().equals(outputAllocationState.getCoo()));
                require.using("The delivery team and platform lead cannot be the same entity.",
                        outputAllocationState.getDeliveryTeam() != outputAllocationState.getPlatformLead());
                require.using("Delivery Team and platform lead must be signers.",
                        command.getSigners().containsAll(Arrays.asList(
                                outputAllocationState.getDeliveryTeam().getOwningKey(),
                                outputAllocationState.getPlatformLead().getOwningKey())));
                require.using("Start date must exist.",
                        outputAllocationState.getStartDate() != null);
                require.using("End date must exist.",
                        outputAllocationState.getEndDate() != null);
                require.using("Project code must exist.",
                        outputAllocationState.getProjectCode() != null);
                require.using("Project code must tally between input and output",
                        inputProjectState.getProjectCode().equals(outputAllocationState.getProjectCode()));
                require.using("Allocation key must exist.",
                        outputAllocationState.getAllocationKey() != null);
                require.using("Allocation key must tally between input and output.",
                        outputAllocationState.getAllocationKey().equals(inputProjectState.getAllocationKey()));

                require.using("Allocation amount must be non-negative.",
                        outputAllocationState.getAllocationAmount() > 0);
                require.using("Allocation amount must be equal to (Input project budget - Output project budget)",
                        outputAllocationState.getAllocationAmount() == inputProjectState.getBudget() - outputProjectState.getBudget());

                require.using("End date cannot be earlier or equal to start date",
                        outputAllocationState.getStartDate().isBefore(outputAllocationState.getEndDate()));
                require.using("Start date cannot be earlier than Project start date",
                        !outputAllocationState.getStartDate().isBefore(inputProjectState.getStartDate()));
                require.using("End date cannot be later than Project end date",
                        !outputAllocationState.getEndDate().isAfter(inputProjectState.getEndDate()));

                return null;
            });
        }else if(command.getValue() instanceof Commands.Update){
            requireThat(require -> {
                require.using("2 inputs should be consumed when updating an allocation.",
                        tx.getInputs().size() == 2);
                require.using("2 output states should be created.",
                        tx.getOutputs().size() == 2);

                final ProjectState inputProjectState = tx.inputsOfType(ProjectState.class).get(0);
                final AllocationState inputAllocationState = tx.inputsOfType(AllocationState.class).get(0);
                final AllocationState outputAllocationState = tx.outputsOfType(AllocationState.class).get(0);

                //checking output allocation state
                require.using("Description must exist.",
                        !outputAllocationState.getDescription().isEmpty());
                require.using("Description must tally between input and output",
                        inputAllocationState.getDescription().equals(outputAllocationState.getDescription()));
                require.using("Platform Lead must exist.",
                        outputAllocationState.getPlatformLead() != null);
                require.using("Platform Lead must tally between input and output",
                        inputAllocationState.getPlatformLead().equals(outputAllocationState.getPlatformLead()));
                require.using("Delivery Team must exist.",
                        outputAllocationState.getDeliveryTeam() != null);
                require.using("Delivery Team must tally between input and output",
                        inputAllocationState.getDeliveryTeam().equals(outputAllocationState.getDeliveryTeam()));
                require.using("COO must exist",
                        outputAllocationState.getCoo() != null);
                require.using("COO must tally between input and output",
                        inputAllocationState.getCoo().equals(outputAllocationState.getCoo()));
                require.using("The delivery team and platform lead cannot be the same entity.",
                        outputAllocationState.getDeliveryTeam() != outputAllocationState.getPlatformLead());
                require.using("COO, Delivery Team and platform lead must be signers.",
                        command.getSigners().containsAll(Arrays.asList(
                                outputAllocationState.getCoo().getOwningKey(),
                                outputAllocationState.getDeliveryTeam().getOwningKey(),
                                outputAllocationState.getPlatformLead().getOwningKey())));
                require.using("Start date must exist.",
                        outputAllocationState.getStartDate() != null);
                require.using("End date must exist.",
                        outputAllocationState.getEndDate() != null);
                require.using("Project code must exist.",
                        outputAllocationState.getProjectCode() != null);
                require.using("Project code must tally between input and output",
                        inputAllocationState.getProjectCode().equals(outputAllocationState.getProjectCode()));
                require.using("Allocation key must exist.",
                        outputAllocationState.getAllocationKey() != null);
                require.using("Allocation key must tally between input and output.",
                        outputAllocationState.getAllocationKey().equals(inputAllocationState.getAllocationKey()));

                require.using("Allocation amount must be non-negative.",
                        outputAllocationState.getAllocationAmount() > 0);

                require.using("End date cannot be earlier or equal to start date",
                        outputAllocationState.getStartDate().isBefore(outputAllocationState.getEndDate()));
                require.using("Start date cannot be earlier than Project start date",
                        !outputAllocationState.getStartDate().isBefore(inputProjectState.getStartDate()));
                require.using("End date cannot be later than Project end date",
                        !outputAllocationState.getEndDate().isAfter(inputProjectState.getEndDate()));

                return null;
            });
        }else{
            throw new IllegalArgumentException("Unrecognised command");
        }

    }

    public interface Commands extends CommandData {
        class Create implements Commands {}
        class Update implements Commands {}
    }
}
