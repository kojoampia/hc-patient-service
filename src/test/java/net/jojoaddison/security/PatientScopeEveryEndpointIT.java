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
    private static final String ALICE_RECORD_ID = "alice-record-1";
    private static final String ORPHAN_RECORD_ID = "orphan-record-1";

    /**
     * The MongoDB field name, which is NOT the Java field name.
     *
     * <p>Every one of these entities maps {@code patientId} to {@code patient_id} with {@code @Field}. Seeding these
     * documents with the Java name instead — as this test did at first — writes a key Spring Data never reads, so the
     * record ends up with no owner at all. The denial assertions below still passed, because a record with no owner
     * is invisible to a patient too; they were proving something much weaker than they appeared to.</p>
     *
     * <p>That is exactly why {@code aPatientsOwnRecordRemainsFullyUsable} exists. A test that only ever asserts
     * "cannot see it" passes just as happily when the fixture is broken as when the security control works.</p>
     */
    private static final String OWNER_FIELD = "patient_id";

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
     * identity, so it needs the tailored setup {@link PatientScopeIT} gives it rather than this generic one.
     * PaymentOption is absent too — it is scoped on {@code userID} rather than {@code patientId}, so the generic
     * document this test inserts would not match; {@link PatientScopeIT} covers it explicitly.</p>
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
            // Scoped on 2026-08-05 by the ownership decision: Address gained a patientId field.
            "/api/addresses,          address",
        }
    )
    void anotherPatientsRecordIsInvisibleThroughEveryVerb(String apiPath, String collection) throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), collection);
        mongoTemplate.save(new Document("_id", BOB_RECORD_ID).append(OWNER_FIELD, BOB_PATIENT_ID), collection);

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
        assertThat(stored.getString(OWNER_FIELD)).as("%s ownership unchanged", collection).isEqualTo(BOB_PATIENT_ID);
    }

    /**
     * The other half of the boundary: a patient must still be able to use their <em>own</em> records.
     *
     * <p>Added after the denial test above had been passing for a while — which it would also do if the scoping were
     * broken in the opposite direction and denied everything. A portal that shows every patient an empty screen is a
     * different bug from one that shows them each other's records, but it is still a bug, and nothing here would have
     * caught it.</p>
     */
    @ParameterizedTest(name = "{0} still serves a patient their own record")
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
            "/api/addresses,          address",
        }
    )
    void aPatientsOwnRecordRemainsFullyUsable(String apiPath, String collection) throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), collection);
        mongoTemplate.save(new Document("_id", ALICE_RECORD_ID).append(OWNER_FIELD, ALICE_PATIENT_ID), collection);

        // Listed, both unfiltered and with her own id as the filter — the dashboard always passes the filter.
        restMockMvc.perform(get(apiPath).with(alice())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        restMockMvc
            .perform(get(apiPath).param("patientId", ALICE_PATIENT_ID).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        // Readable by id.
        restMockMvc
            .perform(get(apiPath + "/{id}", ALICE_RECORD_ID).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ALICE_RECORD_ID));

        // And writable, with ownership preserved rather than dropped.
        restMockMvc
            .perform(
                put(apiPath + "/{id}", ALICE_RECORD_ID)
                    .with(alice())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"" + ALICE_RECORD_ID + "\",\"patientId\":\"" + ALICE_PATIENT_ID + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").value(ALICE_PATIENT_ID));

        // Finally deletable by its owner.
        restMockMvc.perform(delete(apiPath + "/{id}", ALICE_RECORD_ID).with(alice())).andExpect(status().isNoContent());
        assertThat(mongoTemplate.findById(ALICE_RECORD_ID, Document.class, collection)).isNull();
    }

    /**
     * An unrestricted caller patching a record that has no owner and no audit history.
     *
     * <p>Not an edge case: these documents exist. {@code patientId} was added to some collections after data had been
     * written, and the audit fields are only stamped from 2026-08-05 onward — so every record older than that reaches
     * {@code partialUpdate} with nulls where the merge logic checks for them. A patient never sees such a record (an
     * unowned record is visible to nobody), which means an administrator is the only caller who can exercise this
     * path at all.</p>
     */
    @ParameterizedTest(name = "{0} accepts an administrator patching an ownerless record")
    @CsvSource(
        {
            "/api/allergies,       allergy",
            "/api/conditions,      condition",
            "/api/medications,     medication",
            "/api/stats,           stat",
            "/api/memberships,     membership",
            "/api/tasks,           task",
            "/api/reports,         report",
        }
    )
    void anAdministratorCanPatchAnOwnerlessRecord(String apiPath, String collection) throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), collection);
        // Deliberately no patient_id, no created_by, no created_date — a document from before any of this existed.
        mongoTemplate.save(new Document("_id", ORPHAN_RECORD_ID), collection);

        restMockMvc
            .perform(
                patch(apiPath + "/{id}", ORPHAN_RECORD_ID)
                    .with(admin())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + ORPHAN_RECORD_ID + "\"}")
            )
            .andExpect(status().isOk());

        // Still ownerless afterwards: an administrator patching without naming an owner must not silently acquire one.
        Document stored = mongoTemplate.findById(ORPHAN_RECORD_ID, Document.class, collection);
        assertThat(stored).isNotNull();
        assertThat(stored.getString(OWNER_FIELD)).isNull();
    }

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "admin@example.com"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
    }

    private static RequestPostProcessor alice() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, ALICE_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
