package net.jojoaddison.domain;

import static net.jojoaddison.domain.PaymentOptionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PaymentOptionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PaymentOption.class);
        PaymentOption paymentOption1 = getPaymentOptionSample1();
        PaymentOption paymentOption2 = new PaymentOption();
        assertThat(paymentOption1).isNotEqualTo(paymentOption2);

        paymentOption2.setId(paymentOption1.getId());
        assertThat(paymentOption1).isEqualTo(paymentOption2);

        paymentOption2 = getPaymentOptionSample2();
        assertThat(paymentOption1).isNotEqualTo(paymentOption2);
    }
}
