package net.jojoaddison.domain;

import static net.jojoaddison.domain.RecommendationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class RecommendationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Recommendation.class);
        Recommendation recommendation1 = getRecommendationSample1();
        Recommendation recommendation2 = new Recommendation();
        assertThat(recommendation1).isNotEqualTo(recommendation2);

        recommendation2.setId(recommendation1.getId());
        assertThat(recommendation1).isEqualTo(recommendation2);

        recommendation2 = getRecommendationSample2();
        assertThat(recommendation1).isNotEqualTo(recommendation2);
    }
}
