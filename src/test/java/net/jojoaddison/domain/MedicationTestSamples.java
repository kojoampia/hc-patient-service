package net.jojoaddison.domain;

import java.util.UUID;

public class MedicationTestSamples {

    public static Medication getMedicationSample1() {
        return new Medication()
            .id("id1")
            .name("name1")
            .description("description1")
            .patientId("patientId1")
            .caseId("caseId1")
            .prescription("prescription1")
            .dosage("dosage1")
            .prescribedById("prescribedById1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Medication getMedicationSample2() {
        return new Medication()
            .id("id2")
            .name("name2")
            .description("description2")
            .patientId("patientId2")
            .caseId("caseId2")
            .prescription("prescription2")
            .dosage("dosage2")
            .prescribedById("prescribedById2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Medication getMedicationRandomSampleGenerator() {
        return new Medication()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .caseId(UUID.randomUUID().toString())
            .prescription(UUID.randomUUID().toString())
            .dosage(UUID.randomUUID().toString())
            .prescribedById(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
