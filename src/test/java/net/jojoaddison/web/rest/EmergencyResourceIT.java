package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Emergency;
import net.jojoaddison.domain.enumeration.EmergencySeverity;
import net.jojoaddison.domain.enumeration.EmergencyStatus;
import net.jojoaddison.repository.EmergencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link EmergencyResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
/*
 * Runs as ROLE_ADMIN, which {@link net.jojoaddison.security.PatientScope} treats as unrestricted.
 *
 * <p>That is deliberate and it is a narrowing of what this class covers: these tests exercise the CRUD
 * mechanics — status codes, id validation, partial update semantics — and say nothing about who may see
 * what. A default {@code @WithMockUser} is a ROLE_USER with no JWT and therefore no email claim, which now
 * correctly resolves to "no patient" and would make every assertion here fail for reasons unrelated to the
 * behaviour under test.</p>
 *
 * <p>The authorization rules themselves are covered by {@code PatientScopeIT}, which is where a
 * cross-patient regression will be caught. Do not "fix" a failure here by widening PatientScope.</p>
 */
@WithMockUser(authorities = { "ROLE_ADMIN" })
class EmergencyResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_CASE_ID = "AAAAAAAAAA";
    private static final String UPDATED_CASE_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_RAISED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RAISED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_RESOLVED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RESOLVED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_BRIEF = "AAAAAAAAAA";
    private static final String UPDATED_BRIEF = "BBBBBBBBBB";

    private static final String DEFAULT_DETAIL = "AAAAAAAAAA";
    private static final String UPDATED_DETAIL = "BBBBBBBBBB";

    private static final EmergencySeverity DEFAULT_SEVERITY = EmergencySeverity.LOW;
    private static final EmergencySeverity UPDATED_SEVERITY = EmergencySeverity.MODERATE;

    private static final EmergencyStatus DEFAULT_STATUS = EmergencyStatus.RAISED;
    private static final EmergencyStatus UPDATED_STATUS = EmergencyStatus.ACKNOWLEDGED;

    private static final String DEFAULT_OUTCOME = "AAAAAAAAAA";
    private static final String UPDATED_OUTCOME = "BBBBBBBBBB";

    private static final String DEFAULT_LOCATION = "AAAAAAAAAA";
    private static final String UPDATED_LOCATION = "BBBBBBBBBB";

    private static final String DEFAULT_RESPONDENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_RESPONDENT_ID = "BBBBBBBBBB";

    // Audit fields are stamped by the server from the token (AuditStamp), not taken from the request body, so the
    // expected value is the same whatever the test sends. "user" is the login @WithMockUser gives the caller. That
    // these constants no longer vary is the point: an audit field a client can choose is not an audit field.
    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "user";
    private static final String UPDATED_CREATED_BY = "user";

    private static final String DEFAULT_MODIFIED_BY = "user";
    private static final String UPDATED_MODIFIED_BY = "user";

    private static final String ENTITY_API_URL = "/api/emergencies";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private EmergencyRepository emergencyRepository;

    @Autowired
    private MockMvc restEmergencyMockMvc;

    private Emergency emergency;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Emergency createEntity() {
        Emergency emergency = new Emergency()
            .patientId(DEFAULT_PATIENT_ID)
            .caseId(DEFAULT_CASE_ID)
            .raisedAt(DEFAULT_RAISED_AT)
            .resolvedAt(DEFAULT_RESOLVED_AT)
            .brief(DEFAULT_BRIEF)
            .detail(DEFAULT_DETAIL)
            .severity(DEFAULT_SEVERITY)
            .status(DEFAULT_STATUS)
            .outcome(DEFAULT_OUTCOME)
            .location(DEFAULT_LOCATION)
            .respondentId(DEFAULT_RESPONDENT_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return emergency;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Emergency createUpdatedEntity() {
        Emergency emergency = new Emergency()
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .raisedAt(UPDATED_RAISED_AT)
            .resolvedAt(UPDATED_RESOLVED_AT)
            .brief(UPDATED_BRIEF)
            .detail(UPDATED_DETAIL)
            .severity(UPDATED_SEVERITY)
            .status(UPDATED_STATUS)
            .outcome(UPDATED_OUTCOME)
            .location(UPDATED_LOCATION)
            .respondentId(UPDATED_RESPONDENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return emergency;
    }

    @BeforeEach
    public void initTest() {
        emergencyRepository.deleteAll();
        emergency = createEntity();
    }

    @Test
    void createEmergency() throws Exception {
        int databaseSizeBeforeCreate = emergencyRepository.findAll().size();
        // Create the Emergency
        restEmergencyMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(emergency)))
            .andExpect(status().isCreated());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeCreate + 1);
        Emergency testEmergency = emergencyList.get(emergencyList.size() - 1);
        assertThat(testEmergency.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testEmergency.getCaseId()).isEqualTo(DEFAULT_CASE_ID);
        assertThat(testEmergency.getRaisedAt()).isEqualTo(DEFAULT_RAISED_AT);
        assertThat(testEmergency.getResolvedAt()).isEqualTo(DEFAULT_RESOLVED_AT);
        assertThat(testEmergency.getBrief()).isEqualTo(DEFAULT_BRIEF);
        assertThat(testEmergency.getDetail()).isEqualTo(DEFAULT_DETAIL);
        assertThat(testEmergency.getSeverity()).isEqualTo(DEFAULT_SEVERITY);
        assertThat(testEmergency.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testEmergency.getOutcome()).isEqualTo(DEFAULT_OUTCOME);
        assertThat(testEmergency.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testEmergency.getRespondentId()).isEqualTo(DEFAULT_RESPONDENT_ID);
        assertThat(testEmergency.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testEmergency.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testEmergency.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testEmergency.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createEmergencyWithExistingId() throws Exception {
        // Create the Emergency with an existing ID
        emergency.setId("existing_id");

        int databaseSizeBeforeCreate = emergencyRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restEmergencyMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(emergency)))
            .andExpect(status().isBadRequest());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllEmergencies() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        // Get all the emergencyList
        restEmergencyMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(emergency.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].caseId").value(hasItem(DEFAULT_CASE_ID)))
            .andExpect(jsonPath("$.[*].raisedAt").value(hasItem(DEFAULT_RAISED_AT.toString())))
            .andExpect(jsonPath("$.[*].resolvedAt").value(hasItem(DEFAULT_RESOLVED_AT.toString())))
            .andExpect(jsonPath("$.[*].brief").value(hasItem(DEFAULT_BRIEF)))
            .andExpect(jsonPath("$.[*].detail").value(hasItem(DEFAULT_DETAIL)))
            .andExpect(jsonPath("$.[*].severity").value(hasItem(DEFAULT_SEVERITY.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].outcome").value(hasItem(DEFAULT_OUTCOME)))
            .andExpect(jsonPath("$.[*].location").value(hasItem(DEFAULT_LOCATION)))
            .andExpect(jsonPath("$.[*].respondentId").value(hasItem(DEFAULT_RESPONDENT_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllEmergenciesByPatientId() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        // The patient's own records come back
        restEmergencyMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(emergency.getId())));

        // Another patient's id returns nothing rather than everything
        restEmergencyMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getEmergency() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        // Get the emergency
        restEmergencyMockMvc
            .perform(get(ENTITY_API_URL_ID, emergency.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(emergency.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.caseId").value(DEFAULT_CASE_ID))
            .andExpect(jsonPath("$.raisedAt").value(DEFAULT_RAISED_AT.toString()))
            .andExpect(jsonPath("$.resolvedAt").value(DEFAULT_RESOLVED_AT.toString()))
            .andExpect(jsonPath("$.brief").value(DEFAULT_BRIEF))
            .andExpect(jsonPath("$.detail").value(DEFAULT_DETAIL))
            .andExpect(jsonPath("$.severity").value(DEFAULT_SEVERITY.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.outcome").value(DEFAULT_OUTCOME))
            .andExpect(jsonPath("$.location").value(DEFAULT_LOCATION))
            .andExpect(jsonPath("$.respondentId").value(DEFAULT_RESPONDENT_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingEmergency() throws Exception {
        // Get the emergency
        restEmergencyMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingEmergency() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();

        // Update the emergency
        Emergency updatedEmergency = emergencyRepository.findById(emergency.getId()).orElseThrow();
        updatedEmergency
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .raisedAt(UPDATED_RAISED_AT)
            .resolvedAt(UPDATED_RESOLVED_AT)
            .brief(UPDATED_BRIEF)
            .detail(UPDATED_DETAIL)
            .severity(UPDATED_SEVERITY)
            .status(UPDATED_STATUS)
            .outcome(UPDATED_OUTCOME)
            .location(UPDATED_LOCATION)
            .respondentId(UPDATED_RESPONDENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restEmergencyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedEmergency.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedEmergency))
            )
            .andExpect(status().isOk());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
        Emergency testEmergency = emergencyList.get(emergencyList.size() - 1);
        assertThat(testEmergency.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testEmergency.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testEmergency.getRaisedAt()).isEqualTo(UPDATED_RAISED_AT);
        assertThat(testEmergency.getResolvedAt()).isEqualTo(UPDATED_RESOLVED_AT);
        assertThat(testEmergency.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testEmergency.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testEmergency.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testEmergency.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testEmergency.getOutcome()).isEqualTo(UPDATED_OUTCOME);
        assertThat(testEmergency.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testEmergency.getRespondentId()).isEqualTo(UPDATED_RESPONDENT_ID);
        assertThat(testEmergency.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testEmergency.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testEmergency.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testEmergency.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, emergency.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(emergency))
            )
            .andExpect(status().isBadRequest());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(emergency))
            )
            .andExpect(status().isBadRequest());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(emergency)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateEmergencyWithPatch() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();

        // Update the emergency using partial update
        Emergency partialUpdatedEmergency = new Emergency();
        partialUpdatedEmergency.setId(emergency.getId());

        partialUpdatedEmergency
            .raisedAt(UPDATED_RAISED_AT)
            .resolvedAt(UPDATED_RESOLVED_AT)
            .brief(UPDATED_BRIEF)
            .detail(UPDATED_DETAIL)
            .severity(UPDATED_SEVERITY)
            .status(UPDATED_STATUS)
            .respondentId(UPDATED_RESPONDENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restEmergencyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmergency.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedEmergency))
            )
            .andExpect(status().isOk());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
        Emergency testEmergency = emergencyList.get(emergencyList.size() - 1);
        assertThat(testEmergency.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testEmergency.getCaseId()).isEqualTo(DEFAULT_CASE_ID);
        assertThat(testEmergency.getRaisedAt()).isEqualTo(UPDATED_RAISED_AT);
        assertThat(testEmergency.getResolvedAt()).isEqualTo(UPDATED_RESOLVED_AT);
        assertThat(testEmergency.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testEmergency.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testEmergency.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testEmergency.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testEmergency.getOutcome()).isEqualTo(DEFAULT_OUTCOME);
        assertThat(testEmergency.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testEmergency.getRespondentId()).isEqualTo(UPDATED_RESPONDENT_ID);
        assertThat(testEmergency.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testEmergency.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testEmergency.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testEmergency.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateEmergencyWithPatch() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();

        // Update the emergency using partial update
        Emergency partialUpdatedEmergency = new Emergency();
        partialUpdatedEmergency.setId(emergency.getId());

        partialUpdatedEmergency
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .raisedAt(UPDATED_RAISED_AT)
            .resolvedAt(UPDATED_RESOLVED_AT)
            .brief(UPDATED_BRIEF)
            .detail(UPDATED_DETAIL)
            .severity(UPDATED_SEVERITY)
            .status(UPDATED_STATUS)
            .outcome(UPDATED_OUTCOME)
            .location(UPDATED_LOCATION)
            .respondentId(UPDATED_RESPONDENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restEmergencyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmergency.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedEmergency))
            )
            .andExpect(status().isOk());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
        Emergency testEmergency = emergencyList.get(emergencyList.size() - 1);
        assertThat(testEmergency.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testEmergency.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testEmergency.getRaisedAt()).isEqualTo(UPDATED_RAISED_AT);
        assertThat(testEmergency.getResolvedAt()).isEqualTo(UPDATED_RESOLVED_AT);
        assertThat(testEmergency.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testEmergency.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testEmergency.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testEmergency.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testEmergency.getOutcome()).isEqualTo(UPDATED_OUTCOME);
        assertThat(testEmergency.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testEmergency.getRespondentId()).isEqualTo(UPDATED_RESPONDENT_ID);
        assertThat(testEmergency.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testEmergency.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testEmergency.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testEmergency.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, emergency.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(emergency))
            )
            .andExpect(status().isBadRequest());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(emergency))
            )
            .andExpect(status().isBadRequest());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamEmergency() throws Exception {
        int databaseSizeBeforeUpdate = emergencyRepository.findAll().size();
        emergency.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmergencyMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(emergency))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Emergency in the database
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteEmergency() throws Exception {
        // Initialize the database
        emergencyRepository.save(emergency);

        int databaseSizeBeforeDelete = emergencyRepository.findAll().size();

        // Delete the emergency
        restEmergencyMockMvc
            .perform(delete(ENTITY_API_URL_ID, emergency.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Emergency> emergencyList = emergencyRepository.findAll();
        assertThat(emergencyList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
