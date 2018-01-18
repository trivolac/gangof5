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
        final CommandWithParties<ProjectContract.Commands> command = requireSingleCommand(tx.getCommands(), ProjectContract.Commands.class);

        if(command.getValue() instanceof ProjectContract.Commands.Create){
            requireThat(require -> {
                require.using("Only one input state should be consumed when creating a project.",
                        tx.getInputs().size() == 1);
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 2);
                final ProjectState out = tx.outputsOfType(ProjectState.class).get(0);
                final DemandState in = tx.inputsOfType(DemandState.class).get(0);
                require.using("Sponsor must exist.",
                        out.getLender() != null);
                require.using("Platform Lead must exist.",
                        out.getBorrower() != null);
                require.using("CIO must exist.",
                        out.getCio() != null);
                require.using("COO must exist.",
                        out.getCoo() != null);

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

                //CHECK INPUT AGAINST OUTPUT
                require.using("Input description must be equal to output description",
                        out.getDescription().equals(in.getDescription()));

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
