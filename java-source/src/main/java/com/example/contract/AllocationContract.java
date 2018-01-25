package com.example.contract;

import com.example.state.AllocationState;
import com.example.state.ProjectState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class AllocationContract implements Contract {
    public static final String ALLOCATION_CONTRACT_ID = "com.example.contract.AllocationContract";
    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands.Create> command = requireSingleCommand(tx.getCommands(), Commands.Create.class);

        requireThat(require -> {
            require.using("Only 1 input should be consumed when creating an allocation.",
                    tx.getInputs().size() == 1);
            require.using("Two output state should be created.",
                    tx.getOutputs().size() == 2);

            final ProjectState in = tx.inputsOfType(ProjectState.class).get(0);
            final AllocationState outputAllocationState = tx.outputsOfType(AllocationState.class).get(0);
            final ProjectState outputProjectState = tx.outputsOfType(ProjectState.class).get(0);

            //checking output allocation state
            require.using("Delivery Team and platform lead must be the only signers.",
                    command.getSigners().containsAll(outputAllocationState.getParticipants().stream().filter(Objects::nonNull).map(AbstractParty::getOwningKey).collect(Collectors.toList())));
            require.using("Description must exist.",
                    !outputAllocationState.getDescription().isEmpty());
            require.using("Description must tally between input and output",
                    in.getDescription().equals(outputAllocationState.getDescription()));
            require.using("Allocation amount must be non-negative.",
                    outputAllocationState.getAllocationAmount() > 0);
            require.using("Platform Lead must exist.",
                    outputAllocationState.getPlatformLead() != null);
            require.using("Delivery Team must exist.",
                    outputAllocationState.getDeliveryTeam() != null);
            require.using("The delivery team and platform lead cannot be the same entity.",
                    outputAllocationState.getDeliveryTeam() != outputAllocationState.getPlatformLead());
            require.using("Start date must exist.",
                    outputAllocationState.getStartDate() != null);
            require.using("End date must exist.",
                    outputAllocationState.getEndDate() != null);
            require.using("Project code must exist.",
                    outputAllocationState.getProjectCode() != null);
            require.using("Project code must tally between input and output",
                    in.getProjectCode().equals(outputAllocationState.getProjectCode()));
            require.using("Allocation key must exist.",
                    outputAllocationState.getAllocationKey() != null);
            return null;
        });
    }

    public interface Commands extends CommandData {
        class Create implements DemandContract.Commands {}
    }
}
