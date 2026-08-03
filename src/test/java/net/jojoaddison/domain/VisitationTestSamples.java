package net.jojoaddison.domain;

import java.util.UUID;

public class VisitationTestSamples {

    public static Visitation getVisitationSample1() {
        return new Visitation()
            .id("id1")
            .patientId("patientId1")
            .caseId("caseId1")
            .professionalId("professionalId1")
            .purpose("purpose1")
            .location("location1")
            .notes("notes1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Visitation getVisitationSample2() {
        return new Visitation()
            .id("id2")
            .patientId("patientId2")
            .caseId("caseId2")
            .professionalId("professionalId2")
            .purpose("purpose2")
            .location("location2")
            .notes("notes2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Visitation getVisitationRandomSampleGenerator() {
        return new Visitation()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .caseId(UUID.randomUUID().toString())
            .professionalId(UUID.randomUUID().toString())
            .purpose(UUID.randomUUID().toString())
            .location(UUID.randomUUID().toString())
            .notes(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
