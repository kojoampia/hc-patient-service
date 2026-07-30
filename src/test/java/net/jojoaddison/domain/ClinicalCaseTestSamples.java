package net.jojoaddison.domain;

import java.util.UUID;

public class ClinicalCaseTestSamples {

    public static ClinicalCase getClinicalCaseSample1() {
        return new ClinicalCase()
            .id("id1")
            .symptoms("symptoms1")
            .diagnoses("diagnoses1")
            .recommendations("recommendations1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static ClinicalCase getClinicalCaseSample2() {
        return new ClinicalCase()
            .id("id2")
            .symptoms("symptoms2")
            .diagnoses("diagnoses2")
            .recommendations("recommendations2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static ClinicalCase getClinicalCaseRandomSampleGenerator() {
        return new ClinicalCase()
            .id(UUID.randomUUID().toString())
            .symptoms(UUID.randomUUID().toString())
            .diagnoses(UUID.randomUUID().toString())
            .recommendations(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
