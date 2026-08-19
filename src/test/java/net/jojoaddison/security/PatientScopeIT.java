package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Report;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The regression test for the authorization hole closed on 2026-08-05.
 *
 * <p>Before that date this service authorized on {@code .requestMatchers("/api/**").authenticated()} and nothing
 * else. Every entity carried a {@code patientId}, several endpoints accepted it as a query parameter, and nothing
 * ever compared it with the caller — so any account could read, modify and delete every patient's records, and
 * registration is open to the internet. These tests assert the boundary that now exists.</p>
 *
 * <p>Two entities are covered rather than all fourteen, chosen because they are the two <em>shapes</em>:
 * {@code Allergy} returns a plain list and {@code Report} returns a {@code Page}, and they take different paths
 * through {@link PatientScope} ({@code findScoped} vs {@code findScopedPage}). The remaining twelve resources are
 * mechanically identical to one or the other, and {@code TechnicalStructureTest} fails the build if a resource for a
 * patient-owned entity stops consulting {@link PatientScope} at all — that test covers breadth, this one covers
 * depth.</p>
 *
 * <p>Callers are built with the {@code jwt()} post-processor rather than {@code @WithMockUser} because the identity
 * under test lives in a token claim: {@code @WithMockUser} produces no JWT and therefore no {@code email} claim, and
 * a caller who is nobody is a different case from a caller who is somebody.</p>
 *
 * <p>Deliberately flat rather than grouped into {@code @Nested} classes. JHipster's
 * {@code TestContainersSpringContextCustomizerFactory} resolves {@code @EmbeddedMongo} with
 * {@code AnnotatedElementUtils.findMergedAnnotation(testClass, ...)}, which does not walk enclosing classes — so a
 * nested class silently loses the Testcontainers MongoDB and falls back to {@code localhost:27017}. That failure
 * surfaces as an authentication error from a stray local database, nothing like the missing annotation it is.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class PatientScopeIT {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PATIENT_ID = "patient-alice";

    private static final String BOB_EMAIL = "bob@example.com";
    private static final String BOB_PATIENT_ID = "patient-bob";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private Allergy aliceAllergy;
    private Allergy bobAllergy;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        allergyRepository.deleteAll();
        reportRepository.deleteAll();

        profileRepository.save(new Profile().email(ALICE_EMAIL).patientId(ALICE_PATIENT_ID));
        profileRepository.save(new Profile().email(BOB_EMAIL).patientId(BOB_PATIENT_ID));

        aliceAllergy = allergyRepository.save(new Allergy().patientId(ALICE_PATIENT_ID).name("Alice peanut"));
        bobAllergy = allergyRepository.save(new Allergy().patientId(BOB_PATIENT_ID).name("Bob penicillin"));
        reportRepository.save(new Report().patientId(BOB_PATIENT_ID).name("Bob bloods"));
    }

    // --- reads -----------------------------------------------------------------------------------------------

    @Test
    void patientSeesOnlyTheirOwnRecords() throws Exception {
        restMockMvc
            .perform(get("/api/allergies").with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[*].patientId").value(everyItem(is(ALICE_PATIENT_ID))));
    }

    @Test
    void patientCannotWidenScopeByAskingForAnotherPatient() throws Exception {
        // The pre-fix exploit in a single request: the filter came from the caller and was never checked.
        restMockMvc
            .perform(get("/api/allergies").param("patientId", BOB_PATIENT_ID).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotReadAnotherPatientsRecordById() throws Exception {
        restMockMvc.perform(get("/api/allergies/{id}", bobAllergy.getId()).with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void patientStillReadsItsOwnRecordById() throws Exception {
        restMockMvc
            .perform(get("/api/allergies/{id}", aliceAllergy.getId()).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").value(ALICE_PATIENT_ID));
    }

    @Test
    void scopingAppliesToPagedEndpointsToo() throws Exception {
        // Report goes through findScopedPage rather than findScoped — a separate code path, so a separate test.
        restMockMvc
            .perform(get("/api/reports").param("page", "0").param("size", "20").with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        restMockMvc
            .perform(get("/api/reports").param("page", "0").param("size", "20").param("patientId", BOB_PATIENT_ID).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void patientCannotLookUpAnotherPatientsProfileByEmail() throws Exception {
        // The worst instance of the old hole: no record id needed, just a guess at somebody's email address.
        restMockMvc.perform(get("/api/profiles/email/{email}", BOB_EMAIL).with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void patientCanStillLookUpItsOwnProfileByEmail() throws Exception {
        // The dashboard's entry point into the record — it must keep working, case-insensitively.
        restMockMvc
            .perform(get("/api/profiles/email/{email}", ALICE_EMAIL.toUpperCase()).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").value(ALICE_PATIENT_ID));
    }

    @Test
    void administratorMayLookUpAnyProfileByEmail() throws Exception {
        restMockMvc
            .perform(get("/api/profiles/email/{email}", BOB_EMAIL).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").value(BOB_PATIENT_ID));
    }

    // --- writes ----------------------------------------------------------------------------------------------

    @Test
    void patientCannotUpdateAnotherPatientsRecord() throws Exception {
        Allergy payload = new Allergy().patientId(BOB_PATIENT_ID).name("hijacked");
        payload.setId(bobAllergy.getId());

        restMockMvc
            .perform(
                put("/api/allergies/{id}", bobAllergy.getId()).with(alice()).contentType(MediaType.APPLICATION_JSON).content(json(payload))
            )
            .andExpect(status().isBadRequest());

        assertThat(allergyRepository.findById(bobAllergy.getId()).orElseThrow().getName()).isEqualTo("Bob penicillin");
    }

    @Test
    void patientCannotPatchAnotherPatientsRecord() throws Exception {
        Allergy payload = new Allergy();
        payload.setId(bobAllergy.getId());
        payload.setName("hijacked");

        restMockMvc
            .perform(
                patch("/api/allergies/{id}", bobAllergy.getId())
                    .with(alice())
                    .contentType("application/merge-patch+json")
                    .content(json(payload))
            )
            .andExpect(status().isBadRequest());

        assertThat(allergyRepository.findById(bobAllergy.getId()).orElseThrow().getName()).isEqualTo("Bob penicillin");
    }

    @Test
    void patientCannotDeleteAnotherPatientsRecord() throws Exception {
        // 403 rather than 404 since patient data became undeletable: DELETE is refused for every non-administrator
        // before ownership is consulted at all, so this answer no longer depends on whose record it is.
        restMockMvc.perform(delete("/api/allergies/{id}", bobAllergy.getId()).with(alice())).andExpect(status().isForbidden());

        assertThat(allergyRepository.existsById(bobAllergy.getId())).isTrue();
    }

    @Test
    void patientCannotDeleteTheirOwnRecordEither() throws Exception {
        // The companion to the test above, and the one that actually changed policy. Alice may read and edit every
        // field of her own allergy; she may not make it stop existing. A record removed by the person it is about is
        // gone for the clinicians who relied on it too.
        restMockMvc.perform(delete("/api/allergies/{id}", aliceAllergy.getId()).with(alice())).andExpect(status().isForbidden());

        assertThat(allergyRepository.existsById(aliceAllergy.getId())).as("own record survived").isTrue();
    }

    @Test
    void patientCannotAssignANewRecordToAnotherPatient() throws Exception {
        // The body says Bob. The token says Alice. The token wins.
        restMockMvc
            .perform(
                post("/api/allergies")
                    .with(alice())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new Allergy().patientId(BOB_PATIENT_ID).name("smuggled")))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").value(ALICE_PATIENT_ID));

        assertThat(allergyRepository.findByPatientId(BOB_PATIENT_ID)).hasSize(1);
    }

    @Test
    void patientCannotMoveItsOwnRecordToAnotherPatient() throws Exception {
        Allergy payload = new Allergy().patientId(BOB_PATIENT_ID).name("Alice peanut");
        payload.setId(aliceAllergy.getId());

        restMockMvc
            .perform(
                put("/api/allergies/{id}", aliceAllergy.getId())
                    .with(alice())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(payload))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").value(ALICE_PATIENT_ID));
    }

    @Test
    void paymentOptionIsScopedOnItsUserIdField() throws Exception {
        // PaymentOption predates the patientId convention and carries `userID` instead — stored as `user_id`,
        // which is why these documents are written with that key and not the Java field name.
        // PaymentOption predates the patientId convention and carries `userID` instead. It is scoped on that rather
        // than gaining a second owner column — so this asserts the same boundary through a differently named field,
        // which is exactly the case a copy-paste of the other tests would miss.
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), "payment_option");
        mongoTemplate.save(new org.bson.Document("_id", "bob-card").append("user_id", BOB_PATIENT_ID), "payment_option");
        mongoTemplate.save(new org.bson.Document("_id", "alice-card").append("user_id", ALICE_PATIENT_ID), "payment_option");

        restMockMvc
            .perform(get("/api/payment-options").with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].id").value("alice-card"));

        restMockMvc.perform(get("/api/payment-options/{id}", "bob-card").with(alice())).andExpect(status().isNotFound());

        // ...and the write verbs honour the same field. A payment instrument is the one record here where a
        // successful cross-patient write would be worth money to somebody.
        restMockMvc
            .perform(
                put("/api/payment-options/{id}", "bob-card")
                    .with(alice())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"bob-card\",\"userID\":\"" + ALICE_PATIENT_ID + "\"}")
            )
            .andExpect(status().isBadRequest());

        restMockMvc
            .perform(
                patch("/api/payment-options/{id}", "bob-card")
                    .with(alice())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"bob-card\",\"userID\":\"" + ALICE_PATIENT_ID + "\"}")
            )
            .andExpect(status().isBadRequest());

        restMockMvc.perform(delete("/api/payment-options/{id}", "bob-card").with(alice())).andExpect(status().isForbidden());

        assertThat(mongoTemplate.findById("bob-card", org.bson.Document.class, "payment_option")).isNotNull();
    }

    @Test
    void deletingSomethingThatDoesNotExistIs404ForAnyoneIncludingAnAdministrator() throws Exception {
        // The guard reads the record before deleting it, so for an administrator "absent" and "not yours" take the
        // same exit. Worth pinning: it is a behaviour change from the generated code, which answered 204 for a
        // missing id.
        restMockMvc.perform(delete("/api/allergies/{id}", "no-such-record").with(admin())).andExpect(status().isNotFound());
        restMockMvc.perform(delete("/api/addresses/{id}", "no-such-record").with(admin())).andExpect(status().isNotFound());

        // A patient does not get that far. They are refused the verb itself, so a missing id and a real one are
        // indistinguishable to them — which is the point.
        restMockMvc.perform(delete("/api/allergies/{id}", "no-such-record").with(alice())).andExpect(status().isForbidden());
    }

    // --- callers at the edges ---------------------------------------------------------------------------------

    @Test
    void accountWithNoProfileSeesNothingRatherThanEverything() throws Exception {
        // Failing closed is the whole point: the old default was "all records".
        restMockMvc
            .perform(get("/api/allergies").with(patient("nobody@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tokenWithNoEmailClaimSeesNothing() throws Exception {
        // A token minted before this claim existed, or by a product that does not set it.
        RequestPostProcessor legacy = jwt()
            .jwt(builder -> builder.claim("sub", "someone"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER));

        restMockMvc.perform(get("/api/allergies").with(legacy)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void administratorIsUnrestricted() throws Exception {
        restMockMvc.perform(get("/api/allergies").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void professionalIsUnrestricted() throws Exception {
        // The named role is the point: cross-patient access is something granted, not something you get by default
        // when nobody remembers to write a check.
        RequestPostProcessor professional = jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "doctor@example.com"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.PROFESSIONAL));

        restMockMvc.perform(get("/api/allergies").with(professional)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void administratorMayStillFilterToOnePatient() throws Exception {
        restMockMvc
            .perform(get("/api/allergies").param("patientId", BOB_PATIENT_ID).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].patientId").value(BOB_PATIENT_ID));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static RequestPostProcessor alice() {
        return patient(ALICE_EMAIL);
    }

    private static RequestPostProcessor patient(String email) {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, email))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.com"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private static String json(Object value) throws Exception {
        return MAPPER.writeValueAsString(value);
    }
}
