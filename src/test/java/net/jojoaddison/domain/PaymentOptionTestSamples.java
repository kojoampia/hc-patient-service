package net.jojoaddison.domain;

import java.util.UUID;

public class PaymentOptionTestSamples {

    public static PaymentOption getPaymentOptionSample1() {
        return new PaymentOption().id("id1").type("type1").userID("userID1").metadata("metadata1");
    }

    public static PaymentOption getPaymentOptionSample2() {
        return new PaymentOption().id("id2").type("type2").userID("userID2").metadata("metadata2");
    }

    public static PaymentOption getPaymentOptionRandomSampleGenerator() {
        return new PaymentOption()
            .id(UUID.randomUUID().toString())
            .type(UUID.randomUUID().toString())
            .userID(UUID.randomUUID().toString())
            .metadata(UUID.randomUUID().toString());
    }
}
