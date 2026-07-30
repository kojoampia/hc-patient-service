package net.jojoaddison.domain;

import static net.jojoaddison.domain.ClinicalCaseTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

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
}
