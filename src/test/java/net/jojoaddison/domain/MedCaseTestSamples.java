package net.jojoaddison.domain;

import java.util.UUID;

public class MedCaseTestSamples {

    public static MedCase getMedCaseSample1() {
        return new MedCase()
            .id("id1")
            .symptoms("symptoms1")
            .diagnoses("diagnoses1")
            .recommendations("recommendations1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static MedCase getMedCaseSample2() {
        return new MedCase()
            .id("id2")
            .symptoms("symptoms2")
            .diagnoses("diagnoses2")
            .recommendations("recommendations2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static MedCase getMedCaseRandomSampleGenerator() {
        return new MedCase()
            .id(UUID.randomUUID().toString())
            .symptoms(UUID.randomUUID().toString())
            .diagnoses(UUID.randomUUID().toString())
            .recommendations(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
