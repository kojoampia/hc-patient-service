package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ClinicalCaseTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static ClinicalCase getClinicalCaseSample1() {
        return new ClinicalCase()
            .id("id1")
            .patientId("patientId1")
            .caseNumber(1)
            .title("title1")
            .brief("brief1")
            .symptoms("symptoms1")
            .diagnosis("diagnosis1")
            .assignedProfessionalId("assignedProfessionalId1")
            .assignedRosterId("assignedRosterId1");
    }

    public static ClinicalCase getClinicalCaseSample2() {
        return new ClinicalCase()
            .id("id2")
            .patientId("patientId2")
            .caseNumber(2)
            .title("title2")
            .brief("brief2")
            .symptoms("symptoms2")
            .diagnosis("diagnosis2")
            .assignedProfessionalId("assignedProfessionalId2")
            .assignedRosterId("assignedRosterId2");
    }

    public static ClinicalCase getClinicalCaseRandomSampleGenerator() {
        return new ClinicalCase()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .caseNumber(intCount.incrementAndGet())
            .title(UUID.randomUUID().toString())
            .brief(UUID.randomUUID().toString())
            .symptoms(UUID.randomUUID().toString())
            .diagnosis(UUID.randomUUID().toString())
            .assignedProfessionalId(UUID.randomUUID().toString())
            .assignedRosterId(UUID.randomUUID().toString());
    }
}
