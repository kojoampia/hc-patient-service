package net.jojoaddison.domain;

import java.util.UUID;

public class EmergencyTestSamples {

    public static Emergency getEmergencySample1() {
        return new Emergency()
            .id("id1")
            .patientId("patientId1")
            .caseId("caseId1")
            .brief("brief1")
            .detail("detail1")
            .outcome("outcome1")
            .location("location1")
            .respondentId("respondentId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Emergency getEmergencySample2() {
        return new Emergency()
            .id("id2")
            .patientId("patientId2")
            .caseId("caseId2")
            .brief("brief2")
            .detail("detail2")
            .outcome("outcome2")
            .location("location2")
            .respondentId("respondentId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Emergency getEmergencyRandomSampleGenerator() {
        return new Emergency()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .caseId(UUID.randomUUID().toString())
            .brief(UUID.randomUUID().toString())
            .detail(UUID.randomUUID().toString())
            .outcome(UUID.randomUUID().toString())
            .location(UUID.randomUUID().toString())
            .respondentId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
