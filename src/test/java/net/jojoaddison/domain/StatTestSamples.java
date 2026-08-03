package net.jojoaddison.domain;

import java.util.UUID;

public class StatTestSamples {

    public static Stat getStatSample1() {
        return new Stat()
            .id("id1")
            .patientId("patientId1")
            .type("type1")
            .name("name1")
            .description("description1")
            .unit("unit1")
            .note("note1")
            .createdBy("createdBy1");
    }

    public static Stat getStatSample2() {
        return new Stat()
            .id("id2")
            .patientId("patientId2")
            .type("type2")
            .name("name2")
            .description("description2")
            .unit("unit2")
            .note("note2")
            .createdBy("createdBy2");
    }

    public static Stat getStatRandomSampleGenerator() {
        return new Stat()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .unit(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString());
    }
}
