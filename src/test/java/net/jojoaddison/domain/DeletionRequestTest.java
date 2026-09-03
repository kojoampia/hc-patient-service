package net.jojoaddison.domain;

import static net.jojoaddison.domain.DeletionRequestTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DeletionRequestTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DeletionRequest.class);
        DeletionRequest deletionRequest1 = getDeletionRequestSample1();
        DeletionRequest deletionRequest2 = new DeletionRequest();
        assertThat(deletionRequest1).isNotEqualTo(deletionRequest2);

        deletionRequest2.setId(deletionRequest1.getId());
        assertThat(deletionRequest1).isEqualTo(deletionRequest2);

        deletionRequest2 = getDeletionRequestSample2();
        assertThat(deletionRequest1).isNotEqualTo(deletionRequest2);
    }
}
