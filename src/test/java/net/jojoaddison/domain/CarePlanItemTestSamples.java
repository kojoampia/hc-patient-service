package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CarePlanItemTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static CarePlanItem getCarePlanItemSample1() {
        return new CarePlanItem()
            .id("id1")
            .patientId("patientId1")
            .label("label1")
            .detail("detail1")
            .cadence("cadence1")
            .sortOrder(1)
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static CarePlanItem getCarePlanItemSample2() {
        return new CarePlanItem()
            .id("id2")
            .patientId("patientId2")
            .label("label2")
            .detail("detail2")
            .cadence("cadence2")
            .sortOrder(2)
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static CarePlanItem getCarePlanItemRandomSampleGenerator() {
        return new CarePlanItem()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .label(UUID.randomUUID().toString())
            .detail(UUID.randomUUID().toString())
            .cadence(UUID.randomUUID().toString())
            .sortOrder(intCount.incrementAndGet())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
