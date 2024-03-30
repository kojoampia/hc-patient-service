package net.jojoaddison.domain;

import java.util.UUID;

public class MembershipTestSamples {

    public static Membership getMembershipSample1() {
        return new Membership()
            .id("id1")
            .name("name1")
            .description("description1")
            .status("status1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Membership getMembershipSample2() {
        return new Membership()
            .id("id2")
            .name("name2")
            .description("description2")
            .status("status2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Membership getMembershipRandomSampleGenerator() {
        return new Membership()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .status(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
