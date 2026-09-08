package net.jojoaddison.domain;

import java.util.UUID;
import net.jojoaddison.domain.enumeration.MembershipStatus;

public class MembershipTestSamples {

    public static Membership getMembershipSample1() {
        return new Membership()
            .id("id1")
            .patientId("patientId1")
            .name("name1")
            .description("description1")
            .status(MembershipStatus.PENDING)
            .memberNumber("memberNumber1")
            .plan("plan1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Membership getMembershipSample2() {
        return new Membership()
            .id("id2")
            .patientId("patientId2")
            .name("name2")
            .description("description2")
            .status(MembershipStatus.ACTIVE)
            .memberNumber("memberNumber2")
            .plan("plan2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Membership getMembershipRandomSampleGenerator() {
        return new Membership()
            .id(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .status(MembershipStatus.SUSPENDED)
            .memberNumber(UUID.randomUUID().toString())
            .plan(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
