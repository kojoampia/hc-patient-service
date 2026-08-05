package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Proves the patient boundary holds on <em>every</em> patient-owned endpoint, not just the two that
 * {@link PatientScopeIT} exercises in depth.
 *
 * <p>The fix for the 2026-08-05 authorization hole touched fourteen resources with a mechanical edit. Mechanical
 * edits are exactly the kind that get applied thirteen times, and a reviewer reading a diff of that size will not
 * reliably notice the one that was missed. This test does not read the diff — it asks each endpoint directly.</p>
 *
 * <p>Records are inserted straight into MongoDB as raw documents rather than through the domain classes, so one test
 * body covers fourteen unrelated entity types without needing to know a single field name beyond {@code patientId} —
 * which is the only field the authorization rule looks at. Adding an entity here is one line in the {@code @CsvSource}.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class PatientScopeEveryEndpointIT {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PATIENT_ID = "patient-alice";
    private static final String BOB_PATIENT_ID = "patient-bob";

    private static final String BOB_RECORD_ID = "bob-record-1";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        profileRepository.save(new Profile().email(ALICE_EMAIL).patientId(ALICE_PATIENT_ID));
    }

    /**
     * Every patient-owned collection, as {@code apiPath,mongoCollection}.
     *
     * <p>Profile is absent on purpose: Alice's own profile lives in that collection and is what resolves her
     * identity, so it needs the tailored setup {@link PatientScopeIT} gives it rather than this generic one.</p>
     */
    @ParameterizedTest(name = "{0} denies access to another patient''s record")
    @CsvSource(
        {
            "/api/activity-logs,      activity_log",
            "/api/allergies,          allergy",
            "/api/care-plan-items,    care_plan_item",
            "/api/clinical-cases,     clinicalcase",
            "/api/conditions,         condition",
            "/api/emergencies,        emergency",
            "/api/medications,        medication",
            "/api/memberships,        membership",
            "/api/personal-documents, personal_document",
            "/api/reports,            report",
            "/api/stats,              stat",
            "/api/tasks,              task",
            "/api/visitations,        visitation",
        }
    )
    void anotherPatientsRecordIsInvisibleThroughEveryVerb(String apiPath, String collection) throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), collection);
        mongoTemplate.save(new Document("_id", BOB_RECORD_ID).append("patientId", BOB_PATIENT_ID), collection);

        // The collection listing must not include it.
        restMockMvc
            .perform(get(apiPath).param("patientId", BOB_PATIENT_ID).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // Nor may it be reached directly, by any verb.
        restMockMvc.perform(get(apiPath + "/{id}", BOB_RECORD_ID).with(alice())).andExpect(status().isNotFound());

        restMockMvc
            .perform(
                put(apiPath + "/{id}", BOB_RECORD_ID)
                    .with(alice())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"" + BOB_RECORD_ID + "\",\"patientId\":\"" + ALICE_PATIENT_ID + "\"}")
            )
            .andExpect(status().isBadRequest());

        restMockMvc
            .perform(
                patch(apiPath + "/{id}", BOB_RECORD_ID)
                    .with(alice())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + BOB_RECORD_ID + "\",\"patientId\":\"" + ALICE_PATIENT_ID + "\"}")
            )
            .andExpect(status().isBadRequest());

        restMockMvc.perform(delete(apiPath + "/{id}", BOB_RECORD_ID).with(alice())).andExpect(status().isNotFound());

        // And it is still there, still Bob's.
        Document stored = mongoTemplate.findById(BOB_RECORD_ID, Document.class, collection);
        assertThat(stored).as("%s record survived", collection).isNotNull();
        assertThat(stored.getString("patientId")).as("%s ownership unchanged", collection).isEqualTo(BOB_PATIENT_ID);
    }

    private static RequestPostProcessor alice() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, ALICE_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
