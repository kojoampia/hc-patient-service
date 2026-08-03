package net.jojoaddison.domain;

import java.util.UUID;

public class AllergyTestSamples {

    public static Allergy getAllergySample1() {
        return new Allergy()
            .id("id1")
            .patientId("patientId1")
            .name("name1")
            .reaction("reaction1")
            .notedById("notedById1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Allergy getAllergySample2() {
        return new Allergy()
            .id("id2")
            .patientId("patientId2")
            .name("name2")
            .reaction("reaction2")
            .notedById("notedById2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Allergy getAllergyRandomSampleGenerator() {
        return new Allergy()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .reaction(UUID.randomUUID().toString())
            .notedById(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
