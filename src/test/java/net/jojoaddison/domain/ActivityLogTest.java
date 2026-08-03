package net.jojoaddison.domain;

import static net.jojoaddison.domain.ActivityLogTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ActivityLogTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ActivityLog.class);
        ActivityLog activityLog1 = getActivityLogSample1();
        ActivityLog activityLog2 = new ActivityLog();
        assertThat(activityLog1).isNotEqualTo(activityLog2);

        activityLog2.setId(activityLog1.getId());
        assertThat(activityLog1).isEqualTo(activityLog2);

        activityLog2 = getActivityLogSample2();
        assertThat(activityLog1).isNotEqualTo(activityLog2);
    }
}
