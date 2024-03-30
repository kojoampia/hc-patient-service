package net.jojoaddison.domain;

import java.util.UUID;

public class ConditionTestSamples {

    public static Condition getConditionSample1() {
        return new Condition()
            .id("id1")
            .name("name1")
            .description("description1")
            .patientId("patientId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Condition getConditionSample2() {
        return new Condition()
            .id("id2")
            .name("name2")
            .description("description2")
            .patientId("patientId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Condition getConditionRandomSampleGenerator() {
        return new Condition()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
