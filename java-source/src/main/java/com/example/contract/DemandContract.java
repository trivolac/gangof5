package com.example.contract;

import com.example.state.DemandState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.time.LocalDate;
import java.util.List;

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
                require.using("Description must exist.",
                        !out.getDescription().isEmpty());
                require.using("Sponsor must exist.",
                        out.getSponsor() != null);
                require.using("Platform Lead must exist.",
                        out.getPlatformLead() != null);
                require.using("Amount must be 0.",
                        out.getAmount() == 0);
                require.using("No approval parties must exist.",
                        out.getApprovalParties() != null && out.getApprovalParties().isEmpty());

                final List<PublicKey> requiredSigners = ImmutableList.of(
                        out.getSponsor().getOwningKey(),
                        out.getPlatformLead().getOwningKey()
                );
                require.using("Sponsor and platform must be signers.",
                        command.getSigners().containsAll(requiredSigners));

                return null;
            });
        }else if(command.getValue() instanceof Commands.Update){

                requireThat(require -> {
                    require.using("1 input should be consumed when updating a command",
                            tx.getInputs().size() == 1);
                    require.using("Only one output state should be created.",
                            tx.getOutputs().size() == 2);
                    final DemandState outputDemandState = tx.outputsOfType(DemandState.class).get(0);
                    final DemandState inputDemandState = tx.inputsOfType(DemandState.class).get(0);

                    // Demand-specific constraints.
                    require.using("The demand's amount must be non-negative.",
                            outputDemandState.getAmount() > 0);
                    require.using("Description must exist.",
                            !outputDemandState.getDescription().isEmpty());
                    require.using("Sponsor must exist.",
                            outputDemandState.getSponsor() != null);
                    require.using("Platform Lead must exist.",
                            outputDemandState.getPlatformLead() != null);
                    require.using("The sponsor and platform lead cannot be the same entity.",
                            outputDemandState.getSponsor() != outputDemandState.getPlatformLead());
                    require.using("Approval parties must exist.",
                            outputDemandState.getApprovalParties() != null && !outputDemandState.getApprovalParties().isEmpty());
                    require.using("Start date must exist.",
                            outputDemandState.getStartDate() != null);
                    require.using("End date must exist.",
                            outputDemandState.getEndDate() != null);

                    final List<PublicKey> requiredSigners = ImmutableList.of(
                            outputDemandState.getSponsor().getOwningKey(),
                            outputDemandState.getPlatformLead().getOwningKey()
                    );
                    require.using("Sponsor and platform must be signers.",
                            command.getSigners().containsAll(requiredSigners));

                    require.using("The startDate should not be earlier than current date.",
                            outputDemandState.getStartDate().isAfter(LocalDate.now().atTime(0,0)));
                    require.using("The startDate should not be equal to or later than end date.",
                            outputDemandState.getStartDate().isBefore(outputDemandState.getEndDate()));
                    require.using("Description of Input and Output should be the same.",
                            outputDemandState.getDescription().equals(inputDemandState.getDescription()));
                    require.using("Platform Lead of Input and Output should be the same.",
                            outputDemandState.getPlatformLead().equals(inputDemandState.getPlatformLead()));
                    require.using("Sponsor of Input and Output should be the same.",
                            outputDemandState.getSponsor().equals(inputDemandState.getSponsor()));

                    return null;
                });

        }else if(command.getValue() instanceof Commands.Close){
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
        class Close implements Commands{}
    }
}
