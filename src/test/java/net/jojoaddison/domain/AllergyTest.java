package net.jojoaddison.domain;

import static net.jojoaddison.domain.AllergyTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AllergyTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Allergy.class);
        Allergy allergy1 = getAllergySample1();
        Allergy allergy2 = new Allergy();
        assertThat(allergy1).isNotEqualTo(allergy2);

        allergy2.setId(allergy1.getId());
        assertThat(allergy1).isEqualTo(allergy2);

        allergy2 = getAllergySample2();
        assertThat(allergy1).isNotEqualTo(allergy2);
    }
}
