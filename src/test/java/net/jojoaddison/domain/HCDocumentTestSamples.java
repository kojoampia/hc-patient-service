package net.jojoaddison.domain;

import java.util.UUID;

public class HCDocumentTestSamples {

    public static HCDocument getHCDocumentSample1() {
        return new HCDocument().id("id1").name("name1");
    }

    public static HCDocument getHCDocumentSample2() {
        return new HCDocument().id("id2").name("name2");
    }

    public static HCDocument getHCDocumentRandomSampleGenerator() {
        return new HCDocument().id(UUID.randomUUID().toString()).name(UUID.randomUUID().toString());
    }
}
