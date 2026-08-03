package net.jojoaddison.domain;

import static net.jojoaddison.domain.ClinicalCaseTestSamples.*;
import static net.jojoaddison.domain.RecommendationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ClinicalCaseTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ClinicalCase.class);
        ClinicalCase clinicalCase1 = getClinicalCaseSample1();
        ClinicalCase clinicalCase2 = new ClinicalCase();
        assertThat(clinicalCase1).isNotEqualTo(clinicalCase2);

        clinicalCase2.setId(clinicalCase1.getId());
        assertThat(clinicalCase1).isEqualTo(clinicalCase2);

        clinicalCase2 = getClinicalCaseSample2();
        assertThat(clinicalCase1).isNotEqualTo(clinicalCase2);
    }

    @Test
    void recommendationTest() throws Exception {
        ClinicalCase clinicalCase = getClinicalCaseRandomSampleGenerator();
        Recommendation recommendationBack = getRecommendationRandomSampleGenerator();

        clinicalCase.addRecommendation(recommendationBack);
        assertThat(clinicalCase.getRecommendations()).containsOnly(recommendationBack);

        clinicalCase.removeRecommendation(recommendationBack);
        assertThat(clinicalCase.getRecommendations()).doesNotContain(recommendationBack);

        clinicalCase.recommendations(new HashSet<>(Set.of(recommendationBack)));
        assertThat(clinicalCase.getRecommendations()).containsOnly(recommendationBack);

        clinicalCase.setRecommendations(new HashSet<>());
        assertThat(clinicalCase.getRecommendations()).doesNotContain(recommendationBack);
    }
}
