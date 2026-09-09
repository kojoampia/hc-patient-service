package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.event.PatientEventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * That a patient choosing a plan, and an administrator deciding on it, really land on {@code patient-events} in the
 * shape hc-admin reads. <b>Neither test proves their queue dequeues.</b> That needs their side running against this
 * one; hc-admin's consumer exists on their {@code main} and their panel filters {@code planStatus=PENDING}
 * server-side, so the dequeue is reasoned from their code rather than observed. What is proved here is the frame and
 * the shape, which is all this side can prove alone.
 *
 * <p>Read {@code PatientEventRoundTripIT} for why a mocked publisher is not enough on its own: the publisher swallows
 * its failures by design, so a send that throws every time looks exactly like a send that works, and a wrong binding
 * name creates its own destination rather than failing. This runs the whole path — {@code POST /api/memberships}
 * through the real resource, the real publisher, the real binder and a real broker — and reads the frame back off the
 * topic.</p>
 *
 * <p><strong>Every string here is a literal on purpose.</strong> {@code "PlanChosen"} and the four payload keys are a
 * contract with a repository that cannot be compiled against this one; hc-admin's {@code SiblingEventParser}
 * dispatches on the type and reads the keys by name. Written through the constants instead, a rename here would keep
 * both sides' tests green while the event stopped being understood.</p>
 *
 * <p>The caller is built with {@code jwt()} rather than {@code @WithMockUser} for the reason
 * {@link MembershipStatusWriteGuardIT} gives: the identity under test lives in the token's {@code email} claim.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class MembershipPlanEventIT {

    private static final String PATIENT_EMAIL = "Ama.Plan@Example.Test";
    private static final String PATIENT_ID = "patient-ama-plan";

    /** Unique per run, so the poll below cannot pick up a frame another test left on the topic. */
    private static final String PLAN_CODE = "PAWPAW-" + UUID.randomUUID();

    /** The second test's own, so its two frames cannot be confused with the first test's one. */
    private static final String DECIDED_PLAN_CODE = "MELON-" + UUID.randomUUID();

    /** The third test's own, for the same reason. */
    private static final String BACK_OFFICE_PLAN_CODE = "KUBE-" + UUID.randomUUID();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ENTITY_API_URL = "/api/memberships";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        membershipRepository.deleteAll();
        profileRepository.save(new Profile().email(PATIENT_EMAIL).patientId(PATIENT_ID));
    }

    @Test
    void choosingAPlanReachesTheTopicAsPlanChosen() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        // Exactly what web's and mobile's choosePlan post: the plan's code into `plan`, its display name into `name`.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        MAPPER.writeValueAsString(
                            new Membership()
                                .plan(PLAN_CODE)
                                .name("PAWPAW Plan")
                                .description("For a growing family")
                                .status(MembershipStatus.PENDING)
                        )
                    )
            )
            .andExpect(status().isCreated());

        String membershipId = membershipRepository.findByPatientId(PATIENT_ID).getFirst().getId();

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of("patient-events"));

            ConsumerRecord<String, String> received = pollFor(consumer, PLAN_CODE, "PENDING");
            assertThat(received).as("no PlanChosen arrived on patient-events within the timeout").isNotNull();

            // The partition key: the patient's own address, lowercased, so a plan choice sorts in with their account
            // and onboarding events rather than into a partition of its own.
            assertThat(received.key()).isEqualTo("ama.plan@example.test");

            JsonNode event = MAPPER.readTree(received.value());
            assertThat(event.path("type").asText()).isEqualTo("PlanChosen");
            assertThat(event.path("type").asText()).isEqualTo(PatientEventType.PLAN_CHOSEN);
            assertThat(event.path("subject").path("email").asText()).isEqualTo("ama.plan@example.test");
            assertThat(event.path("subject").path("patientId").asText()).isEqualTo(PATIENT_ID);

            JsonNode data = event.path("data");
            assertThat(data.properties().stream().map(java.util.Map.Entry::getKey))
                .as("the payload shape hc-admin will read by name")
                .containsExactlyInAnyOrder("membershipId", "planCode", "planName", "status");
            assertThat(data.path("membershipId").asText()).isEqualTo(membershipId);
            assertThat(data.path("planCode").asText()).isEqualTo(PLAN_CODE);
            assertThat(data.path("planName").asText()).isEqualTo("PAWPAW Plan");
            // PENDING is what a patient's choice is worth: a request, not a subscription. It is read off the saved
            // document rather than hardcoded, so an administrator creating an ACTIVE one is reported honestly.
            assertThat(data.path("status").asText()).isEqualTo("PENDING");
        }
    }

    /**
     * That an administrator's decision reaches the topic too, carrying the status that was persisted.
     *
     * <p>The defect this closes, backlog item 27: {@code PlanChosen} was published on {@code POST} and nowhere else,
     * so an administrator approving a membership through this repo's own screens — {@code PENDING → ACTIVE}, the exact
     * transition item 19's write guard exists to restrict — was invisible next door. Every row on hc-admin's queue was
     * a report that a choice had happened and nothing ever retired one.</p>
     *
     * <p><strong>Two frames for one membership is the design, not an accident.</strong> hc-admin treats
     * {@code PlanChosen} as an update-only disposition keyed on {@code membershipId} and replaces the plan group
     * wholesale, so the second frame updates the row the first created.</p>
     *
     * <p>The negative — that an update which does <em>not</em> move the status announces nothing — is
     * {@code MembershipStatusAnnouncementTest}'s, deliberately: proving an absence over a real broker means waiting
     * out a timeout and calling silence a pass, which is a slow test that cannot fail honestly.</p>
     */
    @Test
    void approvingAMembershipReachesTheTopicCarryingThePersistedStatus() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(patient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        MAPPER.writeValueAsString(
                            new Membership().plan(DECIDED_PLAN_CODE).name("MELON Plan").status(MembershipStatus.PENDING)
                        )
                    )
            )
            .andExpect(status().isCreated());

        String membershipId = membershipRepository.findByPatientId(PATIENT_ID).getFirst().getId();

        // The back-office action the event exists to prompt, through the administrative CRUD surface web ships behind
        // Authority.ADMIN. A patient sending this same body has their status discarded — MembershipStatusWriteGuardIT.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL + "/{id}", membershipId)
                    .with(administrator())
                    .contentType("application/merge-patch+json")
                    .content(MAPPER.writeValueAsString(new Membership().id(membershipId).status(MembershipStatus.ACTIVE)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of("patient-events"));

            ConsumerRecord<String, String> received = pollFor(consumer, DECIDED_PLAN_CODE, "ACTIVE");
            assertThat(received).as("no PlanChosen carrying ACTIVE arrived on patient-events within the timeout").isNotNull();

            // Still the patient's key, not the administrator's. Keying on the caller would file the approval on a
            // different partition from the choice it approves.
            assertThat(received.key()).isEqualTo("ama.plan@example.test");

            JsonNode event = MAPPER.readTree(received.value());
            assertThat(event.path("type").asText()).isEqualTo("PlanChosen");

            JsonNode data = event.path("data");
            assertThat(data.properties().stream().map(java.util.Map.Entry::getKey))
                .as("the same four keys as the choice — this is one more frame, not a new contract")
                .containsExactlyInAnyOrder("membershipId", "planCode", "planName", "status");
            // The same membership, which is what lets hc-admin replace the plan group it already holds rather than
            // adding a second row nothing retires.
            assertThat(data.path("membershipId").asText()).isEqualTo(membershipId);
            assertThat(data.path("planCode").asText()).isEqualTo(DECIDED_PLAN_CODE);
            assertThat(data.path("status").asText()).isEqualTo("ACTIVE");
        }
    }

    /**
     * That a membership an administrator creates without naming a status still announces one.
     *
     * <p>The defect this closes, found reviewing backlog item 30: {@code statusOnCreate} returned the requested status
     * verbatim for an administrator, so the blank {@code <option [ngValue]="null">} on the generated form — which
     * {@code POST}s when the form carries no id — stored a null status. A creation announces
     * <strong>unconditionally</strong>, having no held status to compare against, and {@code putIfPresent} drops an
     * absent key: the frame went out with three of its four fields.</p>
     *
     * <p><strong>That is the dequeue failure at the wrong end of the lifecycle.</strong> hc-admin's
     * {@code setOrUnset} removes {@code plan_status} for the missing key, so the row does not match the
     * {@code planStatus=PENDING} filter their queue is built on — a membership genuinely awaiting a decision never
     * appears on the panel that exists to get it decided, and since only a decision produces another frame, nothing
     * ever heals it. Item 27's asymmetry contains the same mistake on an <em>update</em>; there was nothing
     * containing it here.</p>
     *
     * <p>The key set is asserted, not just the value: a dropped key and a null value are indistinguishable to
     * {@code path(...).asText()}, and it was a dropped key.</p>
     */
    @Test
    void anAdministratorCreatingWithNoStatusStillAnnouncesOne() throws Exception {
        String brokers = environment.getRequiredProperty("spring.cloud.stream.kafka.binder.brokers");

        // patientId explicitly: an administrator has no patient of their own for requirePatientIdForWrite to resolve.
        // No status at all, which is the whole point — MAPPER writes it as an explicit null, exactly as the form does.
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(administrator())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        MAPPER.writeValueAsString(new Membership().patientId(PATIENT_ID).plan(BACK_OFFICE_PLAN_CODE).name("KUBE Plan"))
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));

        String membershipId = membershipRepository
            .findByPatientId(PATIENT_ID)
            .stream()
            .filter(candidate -> BACK_OFFICE_PLAN_CODE.equals(candidate.getPlan()))
            .findFirst()
            .orElseThrow()
            .getId();

        try (KafkaConsumer<String, String> consumer = consumer(brokers)) {
            consumer.subscribe(List.of("patient-events"));

            ConsumerRecord<String, String> received = pollFor(consumer, BACK_OFFICE_PLAN_CODE, "PENDING");
            assertThat(received).as("no PlanChosen carrying a status arrived for an administrator's creation").isNotNull();

            JsonNode data = MAPPER.readTree(received.value()).path("data");
            assertThat(data.properties().stream().map(java.util.Map.Entry::getKey))
                .as("status is the key that went missing — a frame without it silently unsets plan_status next door")
                .containsExactlyInAnyOrder("membershipId", "planCode", "planName", "status");
            assertThat(data.path("membershipId").asText()).isEqualTo(membershipId);
            assertThat(data.path("status").asText()).isEqualTo("PENDING");
        }
    }

    /**
     * Reads until this test's own frame shows up, ignoring anything another test left on the topic.
     *
     * <p>Matched on the plan code <em>and</em> the status, not on the plan code alone: the second test publishes two
     * frames about one membership, and a poll that stopped at the first would assert {@code ACTIVE} against the
     * {@code PENDING} that preceded it.</p>
     *
     * <p>Fifteen seconds rather than the thirty {@code PatientEventRoundTripIT} allows, and deliberately: this suite's
     * {@code junit.jupiter.execution.timeout.default} is thirty, so a poll that ran that long would be killed by the
     * harness first and report {@code TimeoutException} — measured — instead of the assertion below, which says what
     * actually went wrong. A test that fails uninformatively is most of the way to a test nobody trusts.</p>
     */
    private static ConsumerRecord<String, String> pollFor(KafkaConsumer<String, String> consumer, String planCode, String status)
        throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() == null || !record.value().contains(planCode)) {
                    continue;
                }
                JsonNode data = MAPPER.readTree(record.value()).path("data");
                if (planCode.equals(data.path("planCode").asText(null)) && status.equals(data.path("status").asText(null))) {
                    return record;
                }
            }
        }
        return null;
    }

    private static KafkaConsumer<String, String> consumer(String brokers) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", brokers);
        properties.put("group.id", "membership-plan-event-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, PATIENT_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    /** ROLE_ADMIN alone may say where a membership stands — see {@link MembershipStatusWriteGuardIT}. */
    private static RequestPostProcessor administrator() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.test"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }
}
