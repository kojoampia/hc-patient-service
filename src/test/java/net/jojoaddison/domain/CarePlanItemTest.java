package net.jojoaddison.domain;

import static net.jojoaddison.domain.CarePlanItemTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CarePlanItemTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CarePlanItem.class);
        CarePlanItem carePlanItem1 = getCarePlanItemSample1();
        CarePlanItem carePlanItem2 = new CarePlanItem();
        assertThat(carePlanItem1).isNotEqualTo(carePlanItem2);

        carePlanItem2.setId(carePlanItem1.getId());
        assertThat(carePlanItem1).isEqualTo(carePlanItem2);

        carePlanItem2 = getCarePlanItemSample2();
        assertThat(carePlanItem1).isNotEqualTo(carePlanItem2);
    }
}
