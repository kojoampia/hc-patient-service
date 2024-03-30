package net.jojoaddison.domain;

import static net.jojoaddison.domain.ConditionTestSamples.getConditionSample1;
import static net.jojoaddison.domain.ConditionTestSamples.getConditionSample2;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Condition.class);
        Condition condition1 = getConditionSample1();
        Condition condition2 = new Condition();
        assertThat(condition1).isNotEqualTo(condition2);

        condition2.setId(condition1.getId());
        assertThat(condition1).isEqualTo(condition2);

        condition2 = getConditionSample2();
        assertThat(condition1).isNotEqualTo(condition2);
    }
}
