package net.jojoaddison.domain;

import java.util.UUID;

public class ActivityLogTestSamples {

    public static ActivityLog getActivityLogSample1() {
        return new ActivityLog()
            .id("id1")
            .patientId("patientId1")
            .caseId("caseId1")
            .summary("summary1")
            .detail("detail1")
            .authorId("authorId1")
            .createdBy("createdBy1");
    }

    public static ActivityLog getActivityLogSample2() {
        return new ActivityLog()
            .id("id2")
            .patientId("patientId2")
            .caseId("caseId2")
            .summary("summary2")
            .detail("detail2")
            .authorId("authorId2")
            .createdBy("createdBy2");
    }

    public static ActivityLog getActivityLogRandomSampleGenerator() {
        return new ActivityLog()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .caseId(UUID.randomUUID().toString())
            .summary(UUID.randomUUID().toString())
            .detail(UUID.randomUUID().toString())
            .authorId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString());
    }
}
