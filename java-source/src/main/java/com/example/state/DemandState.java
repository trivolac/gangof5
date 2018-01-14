package com.example.state;

import com.example.schema.DemandSchemaV1;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DemandState implements LinearState, QueryableState {
    private final String description;
    private final Integer amount;
    private final Date startDate;
    private final Date endDate;
    private final Party sponsor;
    private final Party platformLead;
    private final List<Party> approvalParties;
    private final UniqueIdentifier linearId;

    public DemandState(String description, Integer amount, Date startDate, Date endDate, Party sponsor, Party platformLead, List<Party> approvalParties) {
        this.description = description;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sponsor = sponsor;
        this.platformLead = platformLead;
        this.approvalParties = approvalParties;
        linearId = new UniqueIdentifier();
    }

    public String getDescription() {
        return description;
    }

    public Integer getAmount() {
        return amount;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
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
            List<String> approvalPartiesStringList = null;//approvalParties.stream().map(Party::toString).collect(Collectors.toList());

            return new DemandSchemaV1.PersistentDemand(
                    this.description, this.amount, this.startDate, this.endDate, this.sponsor.toString(), this.platformLead.toString(),
                    approvalPartiesStringList
                    , this.linearId.getId());
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        List<AbstractParty> partyList = new ArrayList<>();
        partyList.add(sponsor);
        partyList.add(platformLead);
        //partyList.addAll(approvalParties);
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
