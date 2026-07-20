package net.jojoaddison.domain;

import static net.jojoaddison.domain.MedCaseTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class MedCaseTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(MedCase.class);
        MedCase medCase1 = getMedCaseSample1();
        MedCase medCase2 = new MedCase();
        assertThat(medCase1).isNotEqualTo(medCase2);

        medCase2.setId(medCase1.getId());
        assertThat(medCase1).isEqualTo(medCase2);

        medCase2 = getMedCaseSample2();
        assertThat(medCase1).isNotEqualTo(medCase2);
    }
}
