package com.example.contract;

import com.example.state.AllocationState;
import com.example.state.DemandState;
import com.example.state.ProjectState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Arrays;
import java.util.List;

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
                require.using("Two output states should be created.",
                        tx.getOutputs().size() == 2);

                final ProjectState outputProjectState = tx.outputsOfType(ProjectState.class).get(0);
                final DemandState outputDemandState = tx.outputsOfType(DemandState.class).get(0);
                final DemandState inputDemandState = tx.inputsOfType(DemandState.class).get(0);
                require.using("There must be a Project output.",
                        outputProjectState!= null);
                require.using("There must be a Demand output.",
                        outputDemandState!= null);

                require.using("Sponsor must exist.",
                        outputProjectState.getSponsor() != null);
                require.using("Platform Lead must exist.",
                        outputProjectState.getPlatformLead() != null);
                require.using("CIO must exist.",
                        outputProjectState.getCio() != null);
                require.using("COO must exist.",
                        outputProjectState.getCoo() != null);

                require.using("Description must exist.",
                        !outputProjectState.getDescription().isEmpty());
                require.using("Start date must exist.",
                        outputProjectState.getStartDate() != null);
                require.using("End date must exist.",
                        outputProjectState.getEndDate() != null);
                require.using("The startDate should not be equal to or later than end date.",
                        outputProjectState.getStartDate().isBefore(outputProjectState.getEndDate()));
                require.using("Project code must exist.",
                        outputProjectState.getProjectCode() != null);
                require.using("Allocation key must exist.",
                        outputProjectState.getAllocationKey() != null);
                require.using("Budget must be > 0.",
                        outputProjectState.getBudget() > 0);

                //CHECK INPUT AGAINST OUTPUT
                require.using("Input description must be equal to output description",
                        outputProjectState.getDescription().equals(inputDemandState.getDescription()));
                require.using("Input sponsor must be equal to output sponsor",
                        outputProjectState.getSponsor().equals(inputDemandState.getSponsor()));
                require.using("Input platform lead must be equal to output platform lead",
                        outputProjectState.getPlatformLead().equals(inputDemandState.getPlatformLead()));

                //CHECK BOTH OUTPUTS
                require.using("Description of Demand output must be equal to description of Project output",
                        outputProjectState.getDescription().equals(outputDemandState.getDescription()));
                require.using("Sponsor of Demand output must be equal to sponsor of Project output",
                        outputProjectState.getSponsor().equals(outputDemandState.getSponsor()));
                require.using("Platform lead of Demand output must be equal to platform lead of Project output",
                        outputProjectState.getPlatformLead().equals(outputDemandState.getPlatformLead()));
                require.using("Start date of Demand output must be equal to start date of Project output",
                        outputProjectState.getStartDate().equals(outputDemandState.getStartDate()));
                require.using("End date of Demand output must be equal to end date of Project output",
                        outputProjectState.getEndDate().equals(outputDemandState.getEndDate()));
                require.using("Amount of Demand output must be equal to budget of Project output",
                        outputProjectState.getBudget() == outputDemandState.getAmount());

                List<Party> approvalPartiesToCheck = Arrays.asList(outputProjectState.getCoo(), outputProjectState.getCio());
                require.using("Approval parties of Demand output must contain CIO and COO of Project output",
                        outputDemandState.getApprovalParties().containsAll(approvalPartiesToCheck));

                return null;
            });

        }else if(command.getValue() instanceof ProjectContract.Commands.UpdateBudget){
            requireThat(require -> {
                require.using("Only one input state should be consumed when creating a project.",
                        tx.getInputs().size() == 1);
                require.using("Two output states should be created.",
                        tx.getOutputs().size() == 2);

                final ProjectState inputProjectState = tx.inputsOfType(ProjectState.class).get(0);
                final ProjectState outputProjectState = tx.outputsOfType(ProjectState.class).get(0);
                final AllocationState outputAllocationState = tx.outputsOfType(AllocationState.class).get(0);
                require.using("There must be a Project output.",
                        outputProjectState!= null);
                require.using("There must be an Allocation output.",
                        outputAllocationState!= null);

                require.using("Sponsor must exist.",
                        outputProjectState.getSponsor() != null);
                require.using("Platform Lead must exist.",
                        outputProjectState.getPlatformLead() != null);
                require.using("CIO must exist.",
                        outputProjectState.getCio() != null);
                require.using("COO must exist.",
                        outputProjectState.getCoo() != null);

                require.using("Description must exist.",
                        !outputProjectState.getDescription().isEmpty());
                require.using("Start date must exist.",
                        outputProjectState.getStartDate() != null);
                require.using("End date must exist.",
                        outputProjectState.getEndDate() != null);
                require.using("Project code must exist.",
                        outputProjectState.getProjectCode() != null);
                require.using("Allocation key must exist.",
                        outputProjectState.getAllocationKey() != null);
                require.using("Remaining Budget must not be negative.",
                        outputProjectState.getBudget() >= 0);

                //check input against output
                require.using("Input description must be equal to output description",
                        outputProjectState.getDescription().equals(inputProjectState.getDescription()));
                require.using("Input start date must be equal to output start date",
                        outputProjectState.getStartDate().equals(inputProjectState.getStartDate()));
                require.using("Input end date must be equal to output end date",
                        outputProjectState.getEndDate().equals(inputProjectState.getEndDate()));
                require.using("Input project code must be equal to output project code",
                        outputProjectState.getProjectCode().equals(inputProjectState.getProjectCode()));
                require.using("Input allocation key must be equal to output allocation key",
                        outputProjectState.getAllocationKey().equals(inputProjectState.getAllocationKey()));
                require.using("Input sponsor must be equal to output sponsor",
                        outputProjectState.getSponsor().equals(inputProjectState.getSponsor()));
                require.using("Input platform lead must be equal to output platform lead",
                        outputProjectState.getPlatformLead().equals(inputProjectState.getPlatformLead()));
                require.using("Input cio must be equal to output cio",
                        outputProjectState.getCio().equals(inputProjectState.getCio()));
                require.using("Input coo must be equal to output coo",
                        outputProjectState.getCoo().equals(inputProjectState.getCoo()));

                //check output project budget = input project budget - allocation amount
                require.using("Remaining budget must be equal to (input project budget - allocated amount).",
                        outputProjectState.getBudget() == (inputProjectState.getBudget() - outputAllocationState.getAllocationAmount()));

                return null;
            });

        }else{
            throw new IllegalArgumentException("Unrecognised command");
        }
    }

    public interface Commands extends CommandData {
        class Create implements ProjectContract.Commands {}
        class UpdateBudget implements ProjectContract.Commands {}
    }
}
