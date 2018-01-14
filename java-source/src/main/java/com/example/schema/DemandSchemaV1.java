package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DemandSchemaV1 extends MappedSchema {
    public DemandSchemaV1(){
        super(DemandSchema.class, 1, ImmutableList.of(PersistentDemand.class));
    }

    @Entity
    @Table(name = "Demand")
    public static class PersistentDemand extends PersistentState {
        @Column(name = "description") private final String description;
        @Column(name = "amount") private final int amount;
        @Column(name = "startDate") private final Date startDate;
        @Column(name = "endDate") private final Date endDate;
        @Column(name = "sponsor") private final String sponsor;
        @Column(name = "platformLead") private final String platformLead;
        @Column(name = "approvalParties") @ElementCollection
        private final List<String> approvalParties;
        @Column(name = "linearId") private final UUID linearId;

        public PersistentDemand(String description, int amount, Date startDate, Date endDate, String sponsor, String platformLead, List<String> approvalParties, UUID linearId){
            this.description = description;
            this.amount = amount;
            this.startDate = startDate;
            this.endDate = endDate;
            this.sponsor = sponsor;
            this.platformLead = platformLead;
            this.approvalParties = approvalParties;
            this.linearId = linearId;
        }

        public String getDescription() {
            return description;
        }

        public int getAmount() {
            return amount;
        }

        public Date getStartDate() {
            return startDate;
        }

        public Date getEndDate() {
            return endDate;
        }

        public String getSponsor() {
            return sponsor;
        }

        public String getPlatformLead() {
            return platformLead;
        }

        public List<String> getApprovalParties() {
            return approvalParties;
        }

        public UUID getLinearId() {
            return linearId;
        }
    }
}

