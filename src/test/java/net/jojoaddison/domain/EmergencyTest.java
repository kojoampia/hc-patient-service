package net.jojoaddison.domain;

import static net.jojoaddison.domain.EmergencyTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class EmergencyTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Emergency.class);
        Emergency emergency1 = getEmergencySample1();
        Emergency emergency2 = new Emergency();
        assertThat(emergency1).isNotEqualTo(emergency2);

        emergency2.setId(emergency1.getId());
        assertThat(emergency1).isEqualTo(emergency2);

        emergency2 = getEmergencySample2();
        assertThat(emergency1).isNotEqualTo(emergency2);
    }
}
