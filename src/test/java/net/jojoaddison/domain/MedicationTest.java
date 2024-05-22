package net.jojoaddison.domain;

import static net.jojoaddison.domain.MedicationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class MedicationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Medication.class);
        Medication medication1 = getMedicationSample1();
        Medication medication2 = new Medication();
        assertThat(medication1).isNotEqualTo(medication2);

        medication2.setId(medication1.getId());
        assertThat(medication1).isEqualTo(medication2);

        medication2 = getMedicationSample2();
        assertThat(medication1).isNotEqualTo(medication2);
    }
}
