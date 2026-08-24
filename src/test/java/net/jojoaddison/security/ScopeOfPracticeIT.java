package net.jojoaddison.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Scope of practice, over HTTP.
 *
 * <p>{@link ScopeOfPracticeUnitTest} pins the table; this pins that the table is actually consulted on the way in.
 * The two failures worth catching here are opposite and both silent: a discipline refused something it needs, and a
 * discipline allowed something the table says it may not have.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ScopeOfPracticeIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String MEDICATION = "{\"name\":\"Amoxicillin\",\"patientId\":\"patient-1\"}";
    private static final String CASE = "{\"title\":\"A sore throat\",\"patientId\":\"patient-1\"}";
    private static final String STAT = "{\"name\":\"BP\",\"patientId\":\"patient-1\"}";

    private void expectPost(String path, String body, int expected) throws Exception {
        mockMvc
            .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).as("POST %s", path).isEqualTo(expected)
            );
    }

    @Test
    @WithMockUser(username = "grace", authorities = { "ROLE_DOCTOR" })
    void aDoctorMayWriteADiagnosis() throws Exception {
        expectPost("/api/clinical-cases", CASE, 201);
    }

    @Test
    @WithMockUser(username = "nadia", authorities = { "ROLE_NURSE" })
    void aNurseMayRecordVitalsButNotADiagnosis() throws Exception {
        expectPost("/api/stats", STAT, 201);
        expectPost("/api/clinical-cases", CASE, 403);
    }

    @Test
    @WithMockUser(username = "paa", authorities = { "ROLE_PHARMACIST" })
    void aPharmacistMayWriteMedicationsAndNothingElse() throws Exception {
        expectPost("/api/medications", MEDICATION, 201);
        expectPost("/api/clinical-cases", CASE, 403);
        expectPost("/api/stats", STAT, 403);
    }

    @Test
    @WithMockUser(username = "tetteh", authorities = { "ROLE_TECHNICIAN" })
    void aTechnicianMayRecordAReadingButNotTouchTheChart() throws Exception {
        expectPost("/api/stats", STAT, 201);
        expectPost("/api/medications", MEDICATION, 403);
        expectPost("/api/clinical-cases", CASE, 403);
    }

    @Test
    @WithMockUser(username = "efua", authorities = { "ROLE_CARER" })
    void aCarerMayRecordWhatTheyDidButNotPrescribe() throws Exception {
        expectPost("/api/stats", STAT, 201);
        expectPost("/api/medications", MEDICATION, 403);
    }

    @Test
    @WithMockUser(username = "doctor", authorities = { "ROLE_DOCTOR" })
    void theBlanketRoleStillWritesEverything() throws Exception {
        // Thirty existing checks gate on this role. If any of them narrowed, it happened here.
        expectPost("/api/clinical-cases", CASE, 201);
        expectPost("/api/medications", MEDICATION, 201);
        expectPost("/api/stats", STAT, 201);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_ADMIN" })
    void anAdministratorHoldsNoScopeOfPracticeAndIsNotFilteredByOne() throws Exception {
        expectPost("/api/clinical-cases", CASE, 201);
        expectPost("/api/medications", MEDICATION, 201);
    }

    @Test
    @WithMockUser(username = "nadia", authorities = { "ROLE_NURSE" })
    void aClinicalRoleReachesPatientDataAtAll() throws Exception {
        // The cross-stack defect this change fixes. hc-professional's gateway mints ROLE_NURSE and no
        // ROLE_PROFESSIONAL, and the two stacks share a signing key — so before this, a nurse signing in there
        // reached this service, failed every ROLE_PROFESSIONAL check, resolved to no patient, and was served an
        // empty list rather than a refusal. Silent, and indistinguishable from a patient with no records.
        mockMvc.perform(get("/api/clinical-cases")).andExpect(status().isOk());
    }
}
