package net.jojoaddison.domain;

import java.util.UUID;

public class ProfessionalTestSamples {

    public static Professional getProfessionalSample1() {
        return new Professional()
            .id("id1")
            .firstName("firstName1")
            .lastName("lastName1")
            .role("role1")
            .specialty("specialty1")
            .email("email1")
            .phoneNumber("phoneNumber1")
            .imageUrl("imageUrl1")
            .initials("initials1")
            .location("location1")
            .teamId("teamId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Professional getProfessionalSample2() {
        return new Professional()
            .id("id2")
            .firstName("firstName2")
            .lastName("lastName2")
            .role("role2")
            .specialty("specialty2")
            .email("email2")
            .phoneNumber("phoneNumber2")
            .imageUrl("imageUrl2")
            .initials("initials2")
            .location("location2")
            .teamId("teamId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Professional getProfessionalRandomSampleGenerator() {
        return new Professional()
            .id(UUID.randomUUID().toString())
            .firstName(UUID.randomUUID().toString())
            .lastName(UUID.randomUUID().toString())
            .role(UUID.randomUUID().toString())
            .specialty(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .phoneNumber(UUID.randomUUID().toString())
            .imageUrl(UUID.randomUUID().toString())
            .initials(UUID.randomUUID().toString())
            .location(UUID.randomUUID().toString())
            .teamId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
