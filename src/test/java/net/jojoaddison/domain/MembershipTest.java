package net.jojoaddison.domain;

import static net.jojoaddison.domain.MembershipTestSamples.getMembershipSample1;
import static net.jojoaddison.domain.MembershipTestSamples.getMembershipSample2;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class MembershipTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Membership.class);
        Membership membership1 = getMembershipSample1();
        Membership membership2 = new Membership();
        assertThat(membership1).isNotEqualTo(membership2);

        membership2.setId(membership1.getId());
        assertThat(membership1).isEqualTo(membership2);

        membership2 = getMembershipSample2();
        assertThat(membership1).isNotEqualTo(membership2);
    }
}
