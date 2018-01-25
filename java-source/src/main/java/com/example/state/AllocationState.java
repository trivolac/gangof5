package com.example.state;

import com.example.schema.AllocationSchemaV1;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AllocationState implements LinearState, QueryableState{
    private final String projectCode;
    private final String allocationKey;
    private final String description;
    private final Party platformLead;
    private final Party deliveryTeam;
    private final int allocationAmount;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final UniqueIdentifier linearId;

    public AllocationState(String projectCode, String allocationKey, String description, Party platformLead, Party deliveryTeam, int allocationAmount, LocalDateTime startDate, LocalDateTime endDate) {
        this.projectCode = projectCode;
        this.allocationKey = allocationKey;
        this.description = description;
        this.platformLead = platformLead;
        this.deliveryTeam = deliveryTeam;
        this.allocationAmount = allocationAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.linearId =  new UniqueIdentifier();
    }

    public AllocationState(String projectCode, String allocationKey, String description, Party platformLead, Party deliveryTeam, int allocationAmount, LocalDateTime startDate, LocalDateTime endDate, UniqueIdentifier linearId) {
        this.projectCode = projectCode;
        this.allocationKey = allocationKey;
        this.description = description;
        this.platformLead = platformLead;
        this.deliveryTeam = deliveryTeam;
        this.allocationAmount = allocationAmount;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public Party getPlatformLead() {
        return platformLead;
    }

    public Party getDeliveryTeam() {
        return deliveryTeam;
    }

    public int getAllocationAmount() {
        return allocationAmount;
    }

    @JsonFormat(pattern = "dd/MM/yyyy")
    public LocalDateTime getStartDate() {
        return startDate;
    }

    @JsonFormat(pattern = "dd/MM/yyyy")
    public LocalDateTime getEndDate() {
        return endDate;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new AllocationSchemaV1());
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if(schema instanceof AllocationSchemaV1){
            UUID linearIdString = (this.linearId == null) ? null : this.linearId.getId();

            return new AllocationSchemaV1.PersistentAllocation(this.projectCode, this.allocationKey, this.description,
                    this.platformLead.toString(), this.deliveryTeam.toString(), this.allocationAmount,
                    this.startDate, this.endDate, linearIdString);
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(platformLead, deliveryTeam);
    }
}
