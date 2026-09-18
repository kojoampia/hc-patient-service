package net.jojoaddison.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Duration;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * That the fan-out really goes through Kafka: a membership write, out over a real broker, back in through the bound
 * consumer, and onto a stream a browser is holding open.
 *
 * <h2>Why the broker is in the middle of a push to a browser, and why a test has to prove it is still there</h2>
 *
 * <p><strong>An {@code SseEmitter} is a socket held by one JVM, and the write that activates a membership is handled by
 * whichever instance owns the partition hc-admin published on.</strong> Without the hop, a patient sees their own
 * activation only when the two happen to be the same instance. Production runs a single api container today, so an
 * in-process event bus would pass every other test in this repository and break silently the first time a
 * {@code replicas:} line is added to a compose file — which is exactly why backlog item 39 names the Kafka fan-out as
 * the one thing worth keeping from the generated scaffold, and why this test exists rather than a note.</p>
 *
 * <p>The same reasoning {@code PatientEventRoundTripIT} gives applies here twice over: the publisher swallows its
 * failures by design, so a send that throws every time looks exactly like one that works; and a binding name that does
 * not match the {@code @Bean} method creates its own destination rather than failing. Neither is visible without a real
 * broker on the path.</p>
 *
 * <p><b>What this does not prove</b> is that two <em>separate</em> instances fan out to each other — a single context
 * publishes and consumes here. That needs two JVMs and a shared broker, which is a quality-stack exercise rather than a
 * test; what is proved is that the frame leaves the process and comes back, which is the property the two-instance case
 * is built on.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class MembershipStreamRoundTripIT {

    private static final String STREAM_URL = "/api/membership-events";

    private static final String PATIENT_EMAIL = "Ama.RoundTrip@Example.Test";
    private static final String PATIENT_ID = "patient-ama-round-trip";

    /** Unique per run, so a frame another test left on the shared topic cannot satisfy the assertion. */
    private static final String PLAN_CODE = "PAWPAW-" + UUID.randomUUID();

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MembershipService membershipService;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        membershipRepository.deleteAll();
        profileRepository.save(new Profile().email(PATIENT_EMAIL).patientId(PATIENT_ID));
    }

    @Test
    void aMembershipActivationTravelsOverTheBrokerAndReachesTheOpenStream() throws Exception {
        MvcResult stream = restMockMvc.perform(get(STREAM_URL).with(patient())).andExpect(request().asyncStarted()).andReturn();

        Membership pending = membershipService.save(
            new Membership().patientId(PATIENT_ID).plan(PLAN_CODE).name("PAWPAW Plan").status(MembershipStatus.PENDING)
        );
        // The transition item 39 is about: hc-admin's acknowledgement lands here as PENDING -> ACTIVE, and until now
        // the patient learned about it by restarting the app.
        membershipService.activateIfPending(pending.getId());

        await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> {
                String received = stream.getResponse().getContentAsString();
                assertThat(received).as("nothing came back off the topic — check the binding names").contains(pending.getId());
                assertThat(received).contains("event:" + MembershipChangedEvent.TYPE).contains("ACTIVE");
            });
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, PATIENT_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
