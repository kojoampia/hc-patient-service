package net.jojoaddison.domain;

import java.util.UUID;

public class StatTestSamples {

    public static Stat getStatSample1() {
        return new Stat()
            .id("id1")
            .type("type1")
            .name("name1")
            .description("description1")
            .note("note1")
            .patientId("patientId1")
            .createdBy("createdBy1");
    }

    public static Stat getStatSample2() {
        return new Stat()
            .id("id2")
            .type("type2")
            .name("name2")
            .description("description2")
            .note("note2")
            .patientId("patientId2")
            .createdBy("createdBy2");
    }

    public static Stat getStatRandomSampleGenerator() {
        return new Stat()
            .id(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString());
    }
}
