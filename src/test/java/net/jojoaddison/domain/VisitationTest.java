package net.jojoaddison.domain;

import static net.jojoaddison.domain.VisitationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class VisitationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Visitation.class);
        Visitation visitation1 = getVisitationSample1();
        Visitation visitation2 = new Visitation();
        assertThat(visitation1).isNotEqualTo(visitation2);

        visitation2.setId(visitation1.getId());
        assertThat(visitation1).isEqualTo(visitation2);

        visitation2 = getVisitationSample2();
        assertThat(visitation1).isNotEqualTo(visitation2);
    }
}
