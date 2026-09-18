package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.event.MembershipChangedEvent;
import net.jojoaddison.service.event.MembershipStreamConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The two properties {@code GET /api/membership-events} exists to have, and the generated endpoint it replaces had
 * neither.
 *
 * <h2>Headers on connect</h2>
 *
 * <p><strong>A stream that says nothing until the first event is indistinguishable from a broken network.</strong>
 * {@code GET /api/hc-patient-service-kafka/register} was exactly that: probed on the quality stack with a real token,
 * no response line arrived within eight seconds, direct to the api port as well as through the gateway, because it
 * emitted nothing until a Kafka message appeared. A membership can sit {@code PENDING} for as long as the back office
 * takes, so for this feature "the first event" is routinely hours away and a client cannot be left guessing whether it
 * is connected. Asserted here <em>before anything is published</em>, which is the only ordering that means anything.</p>
 *
 * <p>This is the Spring half of that claim — that the handler commits status and headers rather than deferring them.
 * {@code MembershipStreamOnTheWireIT} is the other half: MockMvc cannot see a socket, and "the framework committed the
 * response" is not the same statement as "bytes reached the client".</p>
 *
 * <h2>One patient's change reaches one patient</h2>
 *
 * <p><strong>The security-critical half, and the reason the generated endpoint had to be replaced rather than pointed
 * at a new topic.</strong> {@code KafkaConsumer.register(principal.getName())} kept an emitter per login and then
 * pushed every frame to all of them with no filtering whatever — so a membership activation would have been broadcast
 * to every connected session on the platform. Two streams are opened here from two different patients, one patient's
 * change is delivered, and the other's stream is required to carry nothing.</p>
 *
 * <p><b>Mutated rather than assumed.</b> With the {@code Visibility.allows} check removed from
 * {@code MembershipStreamRegistry.deliver}, {@code aPatientIsNotToldAboutAnotherPatientsMembership} fails naming the
 * leak while every other test in this class stays green — which is the point of it being a separate test rather than an
 * extra assertion on the delivery one.</p>
 *
 * <p>Delivery is driven through {@link MembershipStreamConsumer}'s bound function rather than by calling the registry,
 * so the path under test starts where a Kafka frame arrives. That the frame really travels over a broker to get there
 * is {@code MembershipStreamRoundTripIT}'s claim, not this one's.</p>
 *
 * <p>The caller is built with {@code jwt()} rather than {@code @WithMockUser} for the reason
 * {@code MembershipStatusWriteGuardIT} gives: the identity under test lives in the token's {@code email} claim, which
 * is what {@code PatientScope} resolves to a patient.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class MembershipStreamResourceIT {

    private static final String STREAM_URL = "/api/membership-events";

    private static final String AMA_EMAIL = "Ama.Stream@Example.Test";
    private static final String AMA_PATIENT_ID = "patient-ama-stream";

    private static final String KOFI_EMAIL = "Kofi.Stream@Example.Test";
    private static final String KOFI_PATIENT_ID = "patient-kofi-stream";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipStreamConsumer streamConsumer;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        profileRepository.save(new Profile().email(AMA_EMAIL).patientId(AMA_PATIENT_ID));
        profileRepository.save(new Profile().email(KOFI_EMAIL).patientId(KOFI_PATIENT_ID));
    }

    @Test
    void theStreamAnswersWithItsHeadersBeforeAnyEventExists() throws Exception {
        MvcResult stream = restMockMvc.perform(get(STREAM_URL).with(patient(AMA_EMAIL))).andExpect(request().asyncStarted()).andReturn();

        assertThat(stream.getResponse().getStatus())
            .as("a stream that has not answered yet cannot be told from a broken network")
            .isEqualTo(200);
        assertThat(stream.getResponse().getContentType()).as("the client dispatches on this").startsWith("text/event-stream");
        // Both are for the proxies in front of this service. nginx buffers a proxied response by default, which for a
        // stream means the patient is told in batches or not at all — and the nginx in question belongs to the
        // architect rather than to this repository, so the header is the only lever available from in here.
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(stream.getResponse().getHeader("Cache-Control")).contains("no-cache");
        // Nothing has been published, and that is the ordering that makes the assertions above mean something.
        assertThat(stream.getResponse().getContentAsString()).doesNotContain(MembershipChangedEvent.TYPE);
    }

    @Test
    void aPatientIsToldWhenTheirOwnMembershipChanges() throws Exception {
        MvcResult ama = restMockMvc.perform(get(STREAM_URL).with(patient(AMA_EMAIL))).andExpect(request().asyncStarted()).andReturn();

        deliver(AMA_PATIENT_ID, "membership-ama-1", "ACTIVE");

        String received = ama.getResponse().getContentAsString();
        assertThat(received).contains("event:" + MembershipChangedEvent.TYPE);
        assertThat(received).contains("membership-ama-1").contains("ACTIVE");
    }

    @Test
    void aPatientIsNotToldAboutAnotherPatientsMembership() throws Exception {
        MvcResult ama = restMockMvc.perform(get(STREAM_URL).with(patient(AMA_EMAIL))).andExpect(request().asyncStarted()).andReturn();
        MvcResult kofi = restMockMvc.perform(get(STREAM_URL).with(patient(KOFI_EMAIL))).andExpect(request().asyncStarted()).andReturn();

        deliver(KOFI_PATIENT_ID, "membership-kofi-1", "ACTIVE");

        // Kofi's own stream is asserted first, so that a delivery that reached NOBODY cannot pass this test by
        // default. A leak test whose positive case is not also checked is satisfied by a broken fan-out.
        assertThat(kofi.getResponse().getContentAsString()).as("the delivery has to have happened at all").contains("membership-kofi-1");
        assertThat(ama.getResponse().getContentAsString())
            .as("Ama's stream carried Kofi's membership: the per-patient filter is not being applied")
            .doesNotContain("membership-kofi-1")
            .doesNotContain(MembershipChangedEvent.TYPE);
    }

    @Test
    void anUnauthenticatedCallerGetsNoStream() throws Exception {
        restMockMvc.perform(get(STREAM_URL)).andExpect(status().isUnauthorized());
    }

    /** Hands one frame to the bound function, exactly as the binder would. */
    private void deliver(String patientId, String membershipId, String status) {
        streamConsumer
            .membershipStreamEvents()
            .accept(
                new MembershipChangedEvent(
                    UUID.randomUUID().toString(),
                    MembershipChangedEvent.TYPE,
                    Instant.now(),
                    patientId,
                    membershipId,
                    status
                )
            );
    }

    /**
     * A portal account: {@code ROLE_USER} + {@code ROLE_PATIENT}.
     *
     * <p>Neither is clinical and neither is {@code ROLE_ADMIN}, which matters here — an unrestricted caller is
     * deliberately <em>not</em> narrowed to one patient, so a leak test written with one would pass with the filter
     * removed.</p>
     */
    private static RequestPostProcessor patient(String email) {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, email))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
