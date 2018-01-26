package com.example.state;

import com.example.schema.DemandSchemaV1;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import net.corda.core.serialization.CordaSerializable;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@CordaSerializable
public class DemandState implements LinearState, QueryableState {
    private final String description;
    private final Integer amount;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final Party sponsor;
    private final Party platformLead;
    private final List<Party> approvalParties;
    private final UniqueIdentifier linearId;

    public DemandState(String description, Party sponsor, Party platformLead) {
        this.description = description;
        this.sponsor = sponsor;
        this.platformLead = platformLead;
        this.amount = 0;
        this.startDate = null;
        this.endDate = null;
        this.approvalParties = new ArrayList<>();
        this.linearId = new UniqueIdentifier();
    }

    public DemandState(String description, Party sponsor, Party platformLead,
                       Integer amount, LocalDateTime startDate, LocalDateTime endDate, List<Party> participantList, UniqueIdentifier linearId) {
        this.description = description;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sponsor = sponsor;
        this.platformLead = platformLead;
        this.approvalParties = participantList;
        this.linearId = linearId;
    }

    public String getDescription() {
        return description;
    }

    public Integer getAmount() {
        return amount;
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

    public List<Party> getApprovalParties() {
        return approvalParties;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new DemandSchemaV1());
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if(schema instanceof DemandSchemaV1){
            List<String> approvalPartiesStringList = null;
            if(approvalParties != null){
                approvalPartiesStringList = approvalParties.stream().map(Party::toString).collect(Collectors.toList());
            }

            UUID linearIdString = (this.linearId == null) ? null : this.linearId.getId();

            return new DemandSchemaV1.PersistentDemand(
                    this.description, this.amount, this.startDate, this.endDate, this.sponsor.toString(), this.platformLead.toString(),
                    approvalPartiesStringList
                    , linearIdString);
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    public List<PublicKey> getParticipantKeys() {
        return getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
    }

    public DemandState updateState(Integer amount, LocalDateTime startDate, LocalDateTime endDate, List<Party> participentList, UniqueIdentifier linearId){
        participentList.addAll(this.getApprovalParties());
        return new DemandState(this.description, this.sponsor, this.platformLead, amount, startDate, endDate, participentList, linearId);
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        List<AbstractParty> partyList = new ArrayList<>();
        partyList.add(sponsor);
        partyList.add(platformLead);
        partyList.addAll(approvalParties);
        return partyList;
    }

    @Override
    public String toString() {
        return "DemandState{" +
                "description=" + description +
                ", amount=" + amount +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", sponsor=" + sponsor +
                ", platformLead=" + platformLead +
                ", approvalParties=" + approvalParties +
                ", linearId=" + linearId +
                '}';
    }
}
