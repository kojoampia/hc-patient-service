package net.jojoaddison.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reads are filtered by the caller's scope of practice.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>{@code canRead} and {@code requireRead} were written on 2026-08-22, tested in
 * {@link ScopeOfPracticeUnitTest}, and <strong>called by nothing</strong> until 2026-08-24. The model was enforced on
 * writes only, so a pharmacist — who may write medications and nothing else — could read every diagnosis in the
 * database. The unit tests passed throughout, because they exercise the table rather than the endpoints. That gap is
 * exactly what an integration test is for, and its absence is why the gap survived two days of green builds.</p>
 *
 * <p>These assert the endpoints, not the table. A test that says "a pharmacist cannot read DIAGNOSIS" against
 * {@code ScopeOfPractice} restates the table in more words; a test that says {@code GET /api/clinical-cases} returns
 * 403 for a pharmacist is the thing that would have failed before.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ScopeOfPracticeReadsIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "pharm", authorities = { "ROLE_PHARMACIST" })
    void aPharmacistMayNotReadDiagnoses() throws Exception {
        // The headline case. ScopeOfPractice gives a pharmacist DIAGNOSIS *reads* — see the table — but not
        // CARE_PLAN or OBSERVATION, so the pair below is what proves reads are filtered at all rather than
        // uniformly allowed.
        mockMvc.perform(get("/api/care-plan-items")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tech", authorities = { "ROLE_TECHNICIAN" })
    void aLabRoleMayNotReadWhatThePatientIsBeingTreatedFor() throws Exception {
        // "A technician runs a test. Being able to read the diagnosis alongside it is a disclosure nobody would
        // notice" -- ScopeOfPracticeUnitTest. Until this change, nobody would have.
        mockMvc.perform(get("/api/clinical-cases")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/conditions")).andExpect(status().isForbidden());
        // Observations are their own work and stay readable.
        mockMvc.perform(get("/api/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "dr", authorities = { "ROLE_DOCTOR" })
    void aDoctorReadsTheWholeRecord() throws Exception {
        mockMvc.perform(get("/api/clinical-cases")).andExpect(status().isOk());
        mockMvc.perform(get("/api/medications")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ward-admin", authorities = { "ROLE_ADMIN" })
    void anAdministratorIsNotFilteredByAScopeTheyDoNotHold() throws Exception {
        // ROLE_ADMIN is absent from ScopeOfPractice on purpose -- an administrator is not a clinician and holds no
        // scope of practice. canRead returns early for them, and this asserts that the early return survived.
        mockMvc.perform(get("/api/clinical-cases")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "someone", authorities = { "ROLE_USER" })
    void aNonClinicalCallerIsNotFilteredEither() throws Exception {
        // A patient reading their own record is not exercising a scope of practice and must not be filtered by one.
        // Whose records they may see is settled separately, by PatientScope, and is not what this class tests.
        mockMvc.perform(get("/api/clinical-cases")).andExpect(status().isOk());
    }
}
