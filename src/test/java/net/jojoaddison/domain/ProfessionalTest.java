package net.jojoaddison.domain;

import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ProfessionalTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Professional.class);
        Professional professional1 = getProfessionalSample1();
        Professional professional2 = new Professional();
        assertThat(professional1).isNotEqualTo(professional2);

        professional2.setId(professional1.getId());
        assertThat(professional1).isEqualTo(professional2);

        professional2 = getProfessionalSample2();
        assertThat(professional1).isNotEqualTo(professional2);
    }
}
