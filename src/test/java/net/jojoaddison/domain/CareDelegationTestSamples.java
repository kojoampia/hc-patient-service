package net.jojoaddison.domain;

import java.util.UUID;

public class CareDelegationTestSamples {

    public static CareDelegation getCareDelegationSample1() {
        return new CareDelegation()
            .id("id1")
            .patientId("patientId1")
            .angelEmail("angelEmail1")
            .angelLogin("angelLogin1")
            .angelName("angelName1")
            .angelPhone("angelPhone1")
            .activationRequestedById("activationRequestedById1")
            .activationReason("activationReason1")
            .countersignedById("countersignedById1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static CareDelegation getCareDelegationSample2() {
        return new CareDelegation()
            .id("id2")
            .patientId("patientId2")
            .angelEmail("angelEmail2")
            .angelLogin("angelLogin2")
            .angelName("angelName2")
            .angelPhone("angelPhone2")
            .activationRequestedById("activationRequestedById2")
            .activationReason("activationReason2")
            .countersignedById("countersignedById2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static CareDelegation getCareDelegationRandomSampleGenerator() {
        return new CareDelegation()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .angelEmail(UUID.randomUUID().toString())
            .angelLogin(UUID.randomUUID().toString())
            .angelName(UUID.randomUUID().toString())
            .angelPhone(UUID.randomUUID().toString())
            .activationRequestedById(UUID.randomUUID().toString())
            .activationReason(UUID.randomUUID().toString())
            .countersignedById(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
