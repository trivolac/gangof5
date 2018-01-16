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

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DemandState implements LinearState, QueryableState {
    private final String description;
    private final Integer amount;
    private final Date startDate;
    private final Date endDate;
    private final Party lender;
    private final Party borrower;
    private final List<Party> approvalParties;
    private final UniqueIdentifier linearId;

    public DemandState(String description, Party lender, Party borrower) {
        this.description = description;
        this.lender = lender;
        this.borrower = borrower;
        this.amount = 0;
        this.startDate = null;
        this.endDate = null;
        this.approvalParties = new ArrayList<>();
        this.linearId = new UniqueIdentifier();
    }

    public DemandState(String description, Party lender, Party borrower, Integer amount, Date startDate, Date endDate) {
        this.description = description;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.lender = lender;
        this.borrower = borrower;
        this.approvalParties = null;
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

    public Party getLender() {
        return lender;
    }

    public Party getBorrower() {
        return borrower;
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
                    this.description, this.amount, this.startDate, this.endDate, this.lender.toString(), this.borrower.toString(),
                    approvalPartiesStringList
                    , linearIdString);
        }else{
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    public List<PublicKey> getParticipantKeys() {
        return getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
    }

    public DemandState updateState(String description, Party lender, Party borrower, Integer amount, Date startDate, Date endDate){
        return new DemandState(description, lender, borrower, amount, startDate, endDate);
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        List<AbstractParty> partyList = new ArrayList<>();
        partyList.add(lender);
        partyList.add(borrower);
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
                ", sponsor=" + lender +
                ", platformLead=" + borrower +
                ", approvalParties=" + approvalParties +
                ", linearId=" + linearId +
                '}';
    }
}
