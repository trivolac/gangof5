package com.example.contract;

import com.example.state.DemandState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class DemandContract implements Contract{
    public static final String DEMAND_CONTRACT_ID = "com.example.contract.DemandContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        if(command.getValue() instanceof Commands.Create){
            requireThat(require -> {
                require.using("No inputs should be consumed when creating a demand.",
                        tx.getInputs().isEmpty());
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 1);
                final DemandState out = tx.outputsOfType(DemandState.class).get(0);
                require.using("The sponsor and platform lead cannot be the same entity.",
                        out.getSponsor() != out.getPlatformLead());
                require.using("Sponsors and platform lead must be the only signers.",
                        command.getSigners().containsAll(out.getParticipants().stream().filter(Objects::nonNull).map(AbstractParty::getOwningKey).collect(Collectors.toList())));
                require.using("Description must exist.",
                        !out.getDescription().isEmpty());
                return null;
            });
        }else if(command.getValue() instanceof Commands.Update){
            requireThat(require -> {
                require.using("1 input should be consumed when updating a command",
                        tx.getInputs().size() == 1);
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 1);
                final DemandState out = tx.outputsOfType(DemandState.class).get(0);
                require.using("The sponsor and platform lead cannot be the same entity.",
                        out.getSponsor() != out.getPlatformLead());
                require.using("All of the participants must be signers.",
                        command.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

                // IOU-specific constraints.
                require.using("The demand's amount must be non-negative.",
                        out.getAmount() > 0);

                return null;
            });
        }else if(command.getValue() instanceof Commands.Approve){
            requireThat(require -> {
                require.using("1 input should be consumed when approving a command",
                        tx.getInputs().size() == 1);
                require.using("No output state should be created upon approval.",
                        tx.getOutputs().isEmpty());

                return null;
            });
        }else{
            throw new IllegalArgumentException("Unrecognised command");
        }
    }

    public interface Commands extends CommandData {
        class Create implements Commands{}
        class Update implements Commands{}
        class Approve implements Commands{}
    }
}
