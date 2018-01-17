package com.example.contract;

import com.example.state.DemandState;
import com.example.state.ProjectState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class ProjectContract implements Contract {
    public static final String PROJECT_CONTRACT_ID = "com.example.contract.ProjectContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<DemandContract.Commands> command = requireSingleCommand(tx.getCommands(), DemandContract.Commands.class);

        if(command.getValue() instanceof DemandContract.Commands.Create){
            requireThat(require -> {
                require.using("Only one input state should be consumed when creating a project.",
                        tx.getInputs().size() == 1);
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 1);
                final ProjectState out = tx.outputsOfType(ProjectState.class).get(0);
                final DemandState in = tx.inputsOfType(DemandState.class).get(0);
                require.using("Sponsor must exist.",
                        out.getSponsor() != null);
                require.using("Platform Lead must exist.",
                        out.getPlatformLead() != null);
                require.using("CIO must exist.",
                        out.getCio() != null);
                require.using("COO must exist.",
                        out.getCoo() != null);

                require.using("Sponsors, platform lead, coo and cio must be the only signers.",
                        command.getSigners().containsAll(Arrays.asList(out.getSponsor(), out.getPlatformLead(), out.getCio(), out.getCoo())
                                .stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

                require.using("Description must exist.",
                        !out.getDescription().isEmpty());
                require.using("Start date must exist.",
                        out.getStartDate() != null);
                require.using("End date must exist.",
                        out.getEndDate() != null);
                require.using("Project code must exist.",
                        out.getProjectCode() != null);
                require.using("Allocation key must exist.",
                        out.getAllocationKey() != null);
                require.using("Budget must be > 0.",
                        out.getBudget() > 0);
                require.using("Delivery team must not exist.",
                        out.getDeliveryTeam() == null);

                //CHECK INPUT AGAINST OUTPUT
                require.using("Input description must be equal to output description",
                        out.getDescription().equals(in.getDescription()));
                require.using("Input amount must be equal to output budget",
                        out.getBudget() == in.getAmount());
                require.using("Input sponsor must be equal to output sponsor",
                        out.getSponsor() == in.getLender());
                require.using("Input platform lead must be equal to output platform lead",
                        out.getSponsor() == in.getLender());
                require.using("Input start date must be equal to output start date",
                        out.getStartDate() == in.getStartDate());
                require.using("Input end date must be equal to output end date",
                        out.getEndDate() == in.getEndDate());

                return null;
            });
        }else{
            throw new IllegalArgumentException("Unrecognised command");
        }
    }

    public interface Commands extends CommandData {
        class Create implements ProjectContract.Commands {}
    }
}
