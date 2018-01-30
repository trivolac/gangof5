package com.example.state;

import com.example.schema.ProjectSchemaV1;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProjectState implements LinearState, QueryableState {
    private final String projectCode;
    private final String allocationKey;
    private final String description;
    private final int budget;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final Party sponsor;
    private final Party platformLead;
    private final Party cio;
    private final Party coo;
    private final List<Party> deliveryTeams;
    private final String demandId;
    private final UniqueIdentifier linearId;

    public ProjectState(String projectCode, String allocationKey, String description, int budget, LocalDateTime startDate, LocalDateTime endDate, Party sponsor, Party platformLead, Party cio, Party coo, String demandId) {
        this.projectCode = projectCode;
        this.allocationKey = allocationKey;
        this.description = description;
        this.budget = budget;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sponsor = sponsor;
        this.platformLead = platformLead;
        this.cio = cio;
        this.coo = coo;
        this.deliveryTeams = new ArrayList<>();
        this.demandId = demandId;
        this.linearId = new UniqueIdentifier();
    }

    public ProjectState(String projectCode, String allocationKey, String description, int budget, LocalDateTime startDate, LocalDateTime endDate, Party sponsor, Party platformLead, Party cio, Party coo, List<Party> deliveryTeams, String demandId, UniqueIdentifier linearId) {
        this.projectCode = projectCode;
        this.allocationKey = allocationKey;
        this.description = description;
        this.budget = budget;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sponsor = sponsor;
        this.platformLead = platformLead;
        this.cio = cio;
        this.coo = coo;
        this.deliveryTeams = deliveryTeams;
        this.demandId = demandId;
        this.linearId = linearId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getAllocationKey() {
        return allocationKey;
    }

    public String getDescription() {
        return description;
    }

    public int getBudget() {
        return budget;
    }

    @JsonFormat(pattern = "dd/MM/yyyy")
    public LocalDateTime getStartDate() {
        return startDate;
    }

    @JsonFormat(pattern = "dd/MM/yyyy")
    public LocalDateTime getEndDate() {
        return endDate;
    }

    public Party getSponsor() {
        return sponsor;
    }

    public Party getPlatformLead() {
        return platformLead;
    }

    public Party getCio() {
        return cio;
    }

    public Party getCoo() {
        return coo;
    }

    public List<Party> getDeliveryTeams() {
        return deliveryTeams;
    }

    public String getDemandId() {
        return demandId;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new ProjectSchemaV1());
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if(schema instanceof ProjectSchemaV1){
            UUID uuid = (this.linearId == null) ? null : this.linearId.getId();
            List<String> deliveryTeamsString = this.deliveryTeams.stream()
                    .map(Object::toString).collect(Collectors.toList());

            return new ProjectSchemaV1.PersistentProject(this.projectCode, this.allocationKey, this.description, this.budget, this.startDate, this.endDate, this.sponsor.toString(), this.platformLead.toString(), this.cio.toString(), this.coo.toString(), deliveryTeamsString, this.demandId, uuid);
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        List<AbstractParty> parties = new ArrayList<>();
        parties.add(sponsor);
        parties.add(platformLead);
        parties.add(cio);
        parties.add(coo);
        parties.addAll(this.deliveryTeams);
        return parties;
    }

    public ProjectState updateProjectUponNewAllocation(int amount, Party deliveryTeam){
        if(!this.deliveryTeams.contains(deliveryTeam)){
            List<Party> newDeliveryTeams = new ArrayList<>(this.deliveryTeams);
            newDeliveryTeams.add(deliveryTeam);
            return new ProjectState(this.projectCode, this.allocationKey, this.description, this.budget - amount,
                    this.startDate, this.endDate, this.sponsor, this.platformLead, this.cio, this.coo, newDeliveryTeams,
                    this.demandId, this.linearId);
        }

        return new ProjectState(this.projectCode, this.allocationKey, this.description, this.budget - amount,
                this.startDate, this.endDate, this.sponsor, this.platformLead, this.cio, this.coo, this.deliveryTeams,
                this.demandId, this.linearId);
    }
}

