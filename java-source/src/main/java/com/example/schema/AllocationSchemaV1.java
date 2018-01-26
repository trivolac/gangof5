package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

public class AllocationSchemaV1 extends MappedSchema {
    public AllocationSchemaV1(){
        super(AllocationSchema.class, 1, ImmutableList.of(PersistentAllocation.class));
    }

    @Entity
    @Table(name = "Allocation")
    public static class PersistentAllocation extends PersistentState{
        @Column(name = "projectCode") private final String projectCode;
        @Column(name = "allocationKey") private final String allocationKey;
        @Column(name = "description") private final String description;
        @Column(name = "platformLead") private final String platformLead;
        @Column(name = "deliveryTeam") private final String deliveryTeam;
        @Column(name = "coo") private final String coo;
        @Column(name = "allocationAmount") private final int allocationAmount;
        @Column(name = "startDate") private final LocalDateTime startDate;
        @Column(name = "endDate") private final LocalDateTime endDate;
        @Column(name = "linearId") private final UUID linearId;

        public PersistentAllocation(String projectCode, String allocationKey, String description, String platformLead, String deliveryTeam, String coo, int allocationAmount, LocalDateTime startDate, LocalDateTime endDate, UUID linearId) {
            this.projectCode = projectCode;
            this.allocationKey = allocationKey;
            this.description = description;
            this.platformLead = platformLead;
            this.deliveryTeam = deliveryTeam;
            this.coo = coo;
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

        public String getPlatformLead() {
            return platformLead;
        }

        public String getDeliveryTeam() {
            return deliveryTeam;
        }

        public String getCoo() {
            return coo;
        }

        public int getAllocationAmount() {
            return allocationAmount;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public UUID getLinearId() {
            return linearId;
        }
    }
}
