package net.jojoaddison.domain;

import static net.jojoaddison.domain.DutyRosterTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DutyRosterTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DutyRoster.class);
        DutyRoster dutyRoster1 = getDutyRosterSample1();
        DutyRoster dutyRoster2 = new DutyRoster();
        assertThat(dutyRoster1).isNotEqualTo(dutyRoster2);

        dutyRoster2.setId(dutyRoster1.getId());
        assertThat(dutyRoster1).isEqualTo(dutyRoster2);

        dutyRoster2 = getDutyRosterSample2();
        assertThat(dutyRoster1).isNotEqualTo(dutyRoster2);
    }

    @Test
    void subscribersDefaultToAnEmptySetRatherThanNull() {
        // Callers iterate this without a null check — the setter has to absorb a body that omitted the field, which
        // is what Jackson hands over for a PATCH that only touches the name.
        assertThat(new DutyRoster().getSubscribedProfessionalIds()).isEmpty();

        DutyRoster roster = getDutyRosterSample1();
        roster.setSubscribedProfessionalIds(null);
        assertThat(roster.getSubscribedProfessionalIds()).isEmpty();

        roster.setSubscribedProfessionalIds(Set.of("professional-doctor"));
        assertThat(roster.getSubscribedProfessionalIds()).containsExactly("professional-doctor");
    }
}
