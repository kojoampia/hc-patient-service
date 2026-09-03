package net.jojoaddison.domain;

import static net.jojoaddison.domain.CareDelegationTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CareDelegationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CareDelegation.class);
        CareDelegation careDelegation1 = getCareDelegationSample1();
        CareDelegation careDelegation2 = new CareDelegation();
        assertThat(careDelegation1).isNotEqualTo(careDelegation2);

        careDelegation2.setId(careDelegation1.getId());
        assertThat(careDelegation1).isEqualTo(careDelegation2);

        careDelegation2 = getCareDelegationSample2();
        assertThat(careDelegation1).isNotEqualTo(careDelegation2);
    }
}
