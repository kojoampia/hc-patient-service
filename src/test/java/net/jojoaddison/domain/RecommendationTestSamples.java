package net.jojoaddison.domain;

import java.util.UUID;

public class RecommendationTestSamples {

    public static Recommendation getRecommendationSample1() {
        return new Recommendation().id("id1").label("label1").category("category1");
    }

    public static Recommendation getRecommendationSample2() {
        return new Recommendation().id("id2").label("label2").category("category2");
    }

    public static Recommendation getRecommendationRandomSampleGenerator() {
        return new Recommendation()
            .id(UUID.randomUUID().toString())
            .label(UUID.randomUUID().toString())
            .category(UUID.randomUUID().toString());
    }
}
