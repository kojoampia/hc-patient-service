package net.jojoaddison.domain;

import static net.jojoaddison.domain.ClinicalCaseTestSamples.*;
import static net.jojoaddison.domain.RecommendationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
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

    @Test
    void clinicalCaseTest() throws Exception {
        Recommendation recommendation = getRecommendationRandomSampleGenerator();
        ClinicalCase clinicalCaseBack = getClinicalCaseRandomSampleGenerator();

        recommendation.addClinicalCase(clinicalCaseBack);
        assertThat(recommendation.getClinicalCases()).containsOnly(clinicalCaseBack);
        assertThat(clinicalCaseBack.getRecommendations()).containsOnly(recommendation);

        recommendation.removeClinicalCase(clinicalCaseBack);
        assertThat(recommendation.getClinicalCases()).doesNotContain(clinicalCaseBack);
        assertThat(clinicalCaseBack.getRecommendations()).doesNotContain(recommendation);

        recommendation.clinicalCases(new HashSet<>(Set.of(clinicalCaseBack)));
        assertThat(recommendation.getClinicalCases()).containsOnly(clinicalCaseBack);
        assertThat(clinicalCaseBack.getRecommendations()).containsOnly(recommendation);

        recommendation.setClinicalCases(new HashSet<>());
        assertThat(recommendation.getClinicalCases()).doesNotContain(clinicalCaseBack);
        assertThat(clinicalCaseBack.getRecommendations()).doesNotContain(recommendation);
    }
}
