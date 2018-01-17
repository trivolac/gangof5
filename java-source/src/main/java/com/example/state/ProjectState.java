package com.example.state;

import com.example.schema.ProjectSchemaV1;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ProjectState implements LinearState, QueryableState {
    private final String projectCode;
    private final String allocationKey;
    private final String description;
    private final int budget;
    private final String startDate;
    private final String endDate;
    private final Party sponsor;
    private final Party platformLead;
    private final Party cio;
    private final Party coo;
    private final Party deliveryTeam;
    private final UniqueIdentifier linearId;

    public ProjectState(String projectCode, String allocationKey, String description, int budget, String startDate, String endDate, Party sponsor, Party platformLead, Party cio, Party coo, Party deliveryTeam) {
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
        this.deliveryTeam = deliveryTeam;
        this.linearId = new UniqueIdentifier();
    }

    public ProjectState(String projectCode, String allocationKey, String description, int budget, String startDate, String endDate, Party sponsor, Party platformLead, Party cio, Party coo, Party deliveryTeam, UniqueIdentifier linearId) {
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
        this.deliveryTeam = deliveryTeam;
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

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
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

    public Party getDeliveryTeam() {
        return deliveryTeam;
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

            return new ProjectSchemaV1.PersistentProject(this.projectCode, this.allocationKey, this.description, this.budget, this.startDate, this.endDate, this.sponsor.toString(), this.platformLead.toString(), this.cio.toString(), this.coo.toString(), this.deliveryTeam.toString(), uuid);
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(platformLead, sponsor, cio, coo, deliveryTeam);
    }
}

