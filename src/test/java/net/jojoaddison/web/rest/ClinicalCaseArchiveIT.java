package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.repository.ClinicalCaseRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Archiving a clinical case.
 *
 * <p>Separate from {@link ClinicalCaseResourceIT} because that class runs as {@code ROLE_ADMIN} to exercise CRUD
 * mechanics, and archiving needs a clinical authority — the role is part of what is under test here rather than
 * scaffolding to be worked around.</p>
 *
 * <p>The class default is {@code ROLE_DOCTOR}, which is now the only authority that may archive. It was
 * {@code ROLE_PROFESSIONAL} until 2026-08-24, when that role was removed from the platform entirely; the tests below
 * that name an authority explicitly are the ones asserting who is <em>refused</em>, and they are the point of the
 * class — a default nobody contradicts proves nothing.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "grace", authorities = { "ROLE_DOCTOR" })
class ClinicalCaseArchiveIT {

    private static final String API = "/api/clinical-cases";
    private static final String PATIENT_ID = "patient-1";

    @Autowired
    private ClinicalCaseRepository clinicalCaseRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MockMvc mockMvc;

    private ClinicalCase live;

    @BeforeEach
    void initTest() {
        clinicalCaseRepository.deleteAll();
        live = clinicalCaseRepository.save(new ClinicalCase().patientId(PATIENT_ID).title("A sore throat").status(CaseStatus.OPEN));
    }

    private static String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    @Test
    void archivingStampsWhenWhoAndWhy() throws Exception {
        mockMvc
            .perform(
                post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Resolved at follow-up"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").isNotEmpty())
            .andExpect(jsonPath("$.archivedById").value("grace"))
            .andExpect(jsonPath("$.archiveReason").value("Resolved at follow-up"));

        ClinicalCase stored = clinicalCaseRepository.findById(live.getId()).orElseThrow();
        assertThat(stored.isArchived()).isTrue();
        // Everything it had is still there. Archiving retires a case; it does not empty it.
        assertThat(stored.getTitle()).isEqualTo("A sore throat");
        assertThat(stored.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void theArchivistIsTakenFromTheCallerNotThePayload() throws Exception {
        mockMvc
            .perform(
                post(API + "/{id}/archive", live.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Duplicate\",\"archivedById\":\"somebody-else\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedById").value("grace"));
    }

    @Test
    void anArchiveMustSayWhy() throws Exception {
        // An archive with no reason is the delete this exists to replace.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("   ")))
            .andExpect(status().isBadRequest());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void archivingTwiceIsRefusedRatherThanOverwritingTheFirstArchivist() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Resolved")))
            .andExpect(status().isOk());

        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Something else")))
            .andExpect(status().isBadRequest());

        // The point of refusing: a second 200 would rewrite who retired the case and why, and the first answer is the
        // true one. Two clinicians on the same queue is the ordinary way this happens.
        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchiveReason()).isEqualTo("Resolved");
    }

    @Test
    void anArchivedCaseLeavesTheListButRemainsReadableById() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Resolved")))
            .andExpect(status().isOk());

        mockMvc.perform(get(API + "?patientId=" + PATIENT_ID)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        // Excluded from the working queue, not hidden: a link or a bookmark to an archived case keeps working, and
        // nothing has to be un-archived merely to be read.
        mockMvc
            .perform(get(API + "/{id}", live.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archiveReason").value("Resolved"));
    }

    @Test
    void includeArchivedBringsItBack() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Resolved")))
            .andExpect(status().isOk());

        mockMvc
            .perform(get(API + "?patientId=" + PATIENT_ID + "&includeArchived=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(live.getId()));
    }

    @Test
    void unarchivingReturnsItToTheQueueAndClearsTheStamp() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Wrong row")))
            .andExpect(status().isOk());

        mockMvc
            .perform(post(API + "/{id}/unarchive", live.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").doesNotExist())
            .andExpect(jsonPath("$.archivedById").doesNotExist())
            .andExpect(jsonPath("$.archiveReason").doesNotExist());

        mockMvc
            .perform(get(API + "?patientId=" + PATIENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(live.getId()));
    }

    @Test
    void unarchivingSomethingLiveIsRefused() throws Exception {
        mockMvc.perform(post(API + "/{id}/unarchive", live.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void archivingSomethingThatDoesNotExistIs404() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", "no-such-case").contentType(MediaType.APPLICATION_JSON).content(reason("Resolved")))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "kojo", authorities = { "ROLE_USER", "ROLE_PATIENT" })
    void aPatientMayNotArchiveTheirOwnCase() throws Exception {
        // Archiving is the professional's replacement for a delete nobody has. A patient retiring their own case
        // would be the deletion this whole rule exists to prevent, wearing a different name.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("I feel better")))
            .andExpect(status().isForbidden());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void aPutCannotArchiveACaseBehindTheEndpointsBack() throws Exception {
        // A PUT replaces the document wholesale, so without carrying the stored archive state over, any caller who
        // may edit a case could archive it by setting a field — the ROLE_PROFESSIONAL rule defeated by the one verb
        // nobody thought about, which is exactly how a generic PATCH would have defeated CareDelegation.
        mockMvc
            .perform(
                put(API + "/{id}", live.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":\"" +
                        live.getId() +
                        "\",\"patientId\":\"" +
                        PATIENT_ID +
                        "\",\"title\":\"A sore throat\",\"archivedAt\":\"2020-01-01T00:00:00Z\",\"archivedById\":\"forged\"}"
                    )
            )
            .andExpect(status().isOk());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void aPutCannotUnarchiveACaseEither() throws Exception {
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Resolved")))
            .andExpect(status().isOk());

        mockMvc
            .perform(
                put(API + "/{id}", live.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"" + live.getId() + "\",\"patientId\":\"" + PATIENT_ID + "\",\"title\":\"A sore throat\"}")
            )
            .andExpect(status().isOk());

        ClinicalCase stored = clinicalCaseRepository.findById(live.getId()).orElseThrow();
        assertThat(stored.isArchived()).isTrue();
        assertThat(stored.getArchiveReason()).isEqualTo("Resolved");
    }

    @Test
    void aPatchCannotTouchTheArchiveFields() throws Exception {
        mockMvc
            .perform(
                patch(API + "/{id}", live.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + live.getId() + "\",\"archivedAt\":\"2020-01-01T00:00:00Z\",\"archivedById\":\"forged\"}")
            )
            .andExpect(status().isOk());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void aCaseWrittenBeforeArchivingExistedCountsAsLive() throws Exception {
        // Not hypothetical: every case in the database predates these fields, so its document has no archived_at key
        // at all rather than a null one. A query written as "archived_at equals false" would return none of them and
        // empty every queue in the product; `IsNull` matches a missing field too, which is why it is used.
        clinicalCaseRepository.deleteAll();
        mongoTemplate.insert(
            new Document().append("_id", "legacy-1").append("patient_id", PATIENT_ID).append("title", "Written in 2025"),
            "clinicalcase"
        );

        assertThat(mongoTemplate.findById("legacy-1", Document.class, "clinicalcase")).doesNotContainKey("archived_at");

        mockMvc.perform(get(API + "?patientId=" + PATIENT_ID)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("legacy-1"));
    }

    @Test
    @WithMockUser(username = "dr-adjei", authorities = { "ROLE_DOCTOR" })
    void aDoctorMayArchive() throws Exception {
        // The reason this endpoint moved. This service issues no authorities: hc-professional's gateway mints the
        // eight disciplines and never minted ROLE_PROFESSIONAL, so before this every clinician arriving from that
        // stack got 403 and archiving was unreachable from the portal that owns the case queue. Named explicitly
        // even though it is now the class default, because the default is the thing that changed.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("Episode closed")))
            .andExpect(status().isOk());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchivedAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = "dr-adjei", authorities = { "ROLE_DOCTOR" })
    void aDoctorMayUnarchiveToo() throws Exception {
        // Archiving without the way back is a delete with extra steps, so the two authorities must match.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
            .andExpect(status().isOk());

        mockMvc.perform(post(API + "/{id}/unarchive", live.getId())).andExpect(status().isOk());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "ward-admin", authorities = { "ROLE_ADMIN" })
    void anAdminMayNotArchive() throws Exception {
        // Deliberate, and the reason this is a PreAuthorize rather than requireWrite(DIAGNOSIS): PatientScope
        // returns true for ROLE_ADMIN before it consults ScopeOfPractice at all, so requireWrite would quietly
        // admit the operational role this endpoint's javadoc excludes on purpose.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
            .andExpect(status().isForbidden());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "grace", authorities = { "ROLE_PROFESSIONAL", "ROLE_USER" })
    void theRemovedBlanketRoleMayNotArchive() throws Exception {
        // ROLE_PROFESSIONAL was this endpoint's only accepted authority until 2026-08-24 and no longer exists. It is
        // asserted by literal because the constant is gone, and it is asserted at all because tokens minted before
        // the cutover still carry it: the six accounts that held it must lose this endpoint, not keep it by
        // accident. A 403 here is the whole removal working.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
            .andExpect(status().isForbidden());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "nurse-ama", authorities = { "ROLE_NURSE" })
    void aNurseMayNotArchive() throws Exception {
        // ScopeOfPractice grants a nurse everything except DIAGNOSIS — "asserting what is wrong with the patient is
        // not a nursing act" — and ClinicalDomain maps ClinicalCase to DIAGNOSIS. Widening to the other disciplines
        // would say something this model does not.
        mockMvc
            .perform(post(API + "/{id}/archive", live.getId()).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
            .andExpect(status().isForbidden());

        assertThat(clinicalCaseRepository.findById(live.getId()).orElseThrow().getArchivedAt()).isNull();
    }
}
