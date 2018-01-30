package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
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
        @Column(name = "startDate") private final LocalDateTime startDate;
        @Column(name = "endDate") private final LocalDateTime endDate;
        @Column(name = "sponsor") private final String sponsor;
        @Column(name = "platformLead") private final String platformLead;
        @Column(name = "cio") private final String cio;
        @Column(name = "coo") private final String coo;
        @Column(name = "deliveryTeams") @ElementCollection
        private final List<String> deliveryTeams;
        @Column(name = "demandId") private final String demandId;
        @Column(name = "linearId") private final UUID linearId;

        public PersistentProject(String projectCode, String allocationKey, String description, int budget,
                                 LocalDateTime startDate, LocalDateTime endDate, String sponsor, String platformLead,
                                 String cio, String coo, List<String> deliveryTeams, String demandId, UUID linearId) {
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

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public LocalDateTime getEndDate() {
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

        public List<String> getDeliveryTeams() {
            return deliveryTeams;
        }

        public UUID getLinearId() {
            return linearId;
        }

        public String getDemandId() {
            return demandId;
        }
    }
}
