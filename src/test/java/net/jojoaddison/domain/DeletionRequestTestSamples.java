package net.jojoaddison.domain;

import java.util.UUID;

public class DeletionRequestTestSamples {

    public static DeletionRequest getDeletionRequestSample1() {
        return new DeletionRequest()
            .id("id1")
            .patientId("patientId1")
            .requestedByEmail("requestedByEmail1")
            .requestedByLogin("requestedByLogin1")
            .reason("reason1")
            .completedByLogin("completedByLogin1")
            .rejectedByLogin("rejectedByLogin1")
            .decisionReason("decisionReason1");
    }

    public static DeletionRequest getDeletionRequestSample2() {
        return new DeletionRequest()
            .id("id2")
            .patientId("patientId2")
            .requestedByEmail("requestedByEmail2")
            .requestedByLogin("requestedByLogin2")
            .reason("reason2")
            .completedByLogin("completedByLogin2")
            .rejectedByLogin("rejectedByLogin2")
            .decisionReason("decisionReason2");
    }

    public static DeletionRequest getDeletionRequestRandomSampleGenerator() {
        return new DeletionRequest()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .requestedByEmail(UUID.randomUUID().toString())
            .requestedByLogin(UUID.randomUUID().toString())
            .reason(UUID.randomUUID().toString())
            .completedByLogin(UUID.randomUUID().toString())
            .rejectedByLogin(UUID.randomUUID().toString())
            .decisionReason(UUID.randomUUID().toString());
    }
}
