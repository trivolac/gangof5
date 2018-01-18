package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;
import java.util.UUID;

public class ProjectSchemaV1 extends MappedSchema {
    public ProjectSchemaV1(){
        super(ProjectSchema.class, 1, ImmutableList.of(PersistentProject.class));
    }

    @Entity
    @Table(name = "Project")
    public static class PersistentProject extends PersistentState {
        @Column(name = "projectCode") private final String projectCode;
        @Column(name = "allocationKey") private final String allocationKey;
        @Column(name = "description") private final String description;
        @Column(name = "budget") private final int budget;
        @Column(name = "startDate") private final String startDate;
        @Column(name = "endDate") private final String endDate;
        @Column(name = "sponsor") private final String sponsor;
        @Column(name = "platformLead") private final String platformLead;
        @Column(name = "cio") private final String cio;
        @Column(name = "coo") private final String coo;
        @Column(name = "deliveryTeam") private final String deliveryTeam;
        @Column(name = "demandId") private final String demandId;
        @Column(name = "linearId") private final UUID linearId;

        public PersistentProject(String projectCode, String allocationKey, String description, int budget, String startDate, String endDate, String sponsor, String platformLead, String cio, String coo, String deliveryTeam, String demandId, UUID linearId) {
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

        public String getStartDate() {
            return startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public String getSponsor() {
            return sponsor;
        }

        public String getPlatformLead() {
            return platformLead;
        }

        public String getCio() {
            return cio;
        }

        public String getCoo() {
            return coo;
        }

        public String getDeliveryTeam() {
            return deliveryTeam;
        }

        public UUID getLinearId() {
            return linearId;
        }

        public String getDemandId() {
            return demandId;
        }
    }
}
