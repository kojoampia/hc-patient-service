package net.jojoaddison.domain;

import java.util.UUID;

public class PersonalDocumentTestSamples {

    public static PersonalDocument getPersonalDocumentSample1() {
        return new PersonalDocument().id("id1").name("name1").category("category1").url("url1").patientId("patientId1");
    }

    public static PersonalDocument getPersonalDocumentSample2() {
        return new PersonalDocument().id("id2").name("name2").category("category2").url("url2").patientId("patientId2");
    }

    public static PersonalDocument getPersonalDocumentRandomSampleGenerator() {
        return new PersonalDocument()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .category(UUID.randomUUID().toString())
            .url(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString());
    }
}
