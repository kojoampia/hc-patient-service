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
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.domain.enumeration.ActivityKind;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.repository.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ActivityLogResource} REST controller.
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
class ActivityLogResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_CASE_ID = "AAAAAAAAAA";
    private static final String UPDATED_CASE_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_LOGGED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_LOGGED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_SUMMARY = "AAAAAAAAAA";
    private static final String UPDATED_SUMMARY = "BBBBBBBBBB";

    private static final String DEFAULT_DETAIL = "AAAAAAAAAA";
    private static final String UPDATED_DETAIL = "BBBBBBBBBB";

    private static final ActivityKind DEFAULT_KIND = ActivityKind.CASE;
    private static final ActivityKind UPDATED_KIND = ActivityKind.VITAL;

    private static final ActivitySource DEFAULT_SOURCE = ActivitySource.PATIENT;
    private static final ActivitySource UPDATED_SOURCE = ActivitySource.PROFESSIONAL;

    private static final String DEFAULT_AUTHOR_ID = "AAAAAAAAAA";
    private static final String UPDATED_AUTHOR_ID = "BBBBBBBBBB";

    // Audit fields are stamped by the server from the token (AuditStamp), not taken from the request body, so the
    // expected value is the same whatever the test sends. "user" is the login @WithMockUser gives the caller. That
    // these constants no longer vary is the point: an audit field a client can choose is not an audit field.
    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "user";
    private static final String UPDATED_CREATED_BY = "user";

    private static final String ENTITY_API_URL = "/api/activity-logs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private MockMvc restActivityLogMockMvc;

    private ActivityLog activityLog;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ActivityLog createEntity() {
        ActivityLog activityLog = new ActivityLog()
            .patientId(DEFAULT_PATIENT_ID)
            .caseId(DEFAULT_CASE_ID)
            .loggedAt(DEFAULT_LOGGED_AT)
            .summary(DEFAULT_SUMMARY)
            .detail(DEFAULT_DETAIL)
            .kind(DEFAULT_KIND)
            .source(DEFAULT_SOURCE)
            .authorId(DEFAULT_AUTHOR_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY);
        return activityLog;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ActivityLog createUpdatedEntity() {
        ActivityLog activityLog = new ActivityLog()
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .loggedAt(UPDATED_LOGGED_AT)
            .summary(UPDATED_SUMMARY)
            .detail(UPDATED_DETAIL)
            .kind(UPDATED_KIND)
            .source(UPDATED_SOURCE)
            .authorId(UPDATED_AUTHOR_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);
        return activityLog;
    }

    @BeforeEach
    public void initTest() {
        activityLogRepository.deleteAll();
        activityLog = createEntity();
    }

    @Test
    void createActivityLog() throws Exception {
        int databaseSizeBeforeCreate = activityLogRepository.findAll().size();
        // Create the ActivityLog
        restActivityLogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(activityLog)))
            .andExpect(status().isCreated());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeCreate + 1);
        ActivityLog testActivityLog = activityLogList.get(activityLogList.size() - 1);
        assertThat(testActivityLog.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testActivityLog.getCaseId()).isEqualTo(DEFAULT_CASE_ID);
        assertThat(testActivityLog.getLoggedAt()).isEqualTo(DEFAULT_LOGGED_AT);
        assertThat(testActivityLog.getSummary()).isEqualTo(DEFAULT_SUMMARY);
        assertThat(testActivityLog.getDetail()).isEqualTo(DEFAULT_DETAIL);
        assertThat(testActivityLog.getKind()).isEqualTo(DEFAULT_KIND);
        assertThat(testActivityLog.getSource()).isEqualTo(DEFAULT_SOURCE);
        assertThat(testActivityLog.getAuthorId()).isEqualTo(DEFAULT_AUTHOR_ID);
        assertThat(testActivityLog.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testActivityLog.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
    }

    @Test
    void createActivityLogWithExistingId() throws Exception {
        // Create the ActivityLog with an existing ID
        activityLog.setId("existing_id");

        int databaseSizeBeforeCreate = activityLogRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restActivityLogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(activityLog)))
            .andExpect(status().isBadRequest());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllActivityLogs() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        // Get all the activityLogList
        restActivityLogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(activityLog.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].caseId").value(hasItem(DEFAULT_CASE_ID)))
            .andExpect(jsonPath("$.[*].loggedAt").value(hasItem(DEFAULT_LOGGED_AT.toString())))
            .andExpect(jsonPath("$.[*].summary").value(hasItem(DEFAULT_SUMMARY)))
            .andExpect(jsonPath("$.[*].detail").value(hasItem(DEFAULT_DETAIL)))
            .andExpect(jsonPath("$.[*].kind").value(hasItem(DEFAULT_KIND.toString())))
            .andExpect(jsonPath("$.[*].source").value(hasItem(DEFAULT_SOURCE.toString())))
            .andExpect(jsonPath("$.[*].authorId").value(hasItem(DEFAULT_AUTHOR_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }

    @Test
    void getAllActivityLogsByPatientId() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        // The patient's own records come back
        restActivityLogMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(activityLog.getId())));

        // Another patient's id returns nothing rather than everything
        restActivityLogMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getActivityLog() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        // Get the activityLog
        restActivityLogMockMvc
            .perform(get(ENTITY_API_URL_ID, activityLog.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(activityLog.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.caseId").value(DEFAULT_CASE_ID))
            .andExpect(jsonPath("$.loggedAt").value(DEFAULT_LOGGED_AT.toString()))
            .andExpect(jsonPath("$.summary").value(DEFAULT_SUMMARY))
            .andExpect(jsonPath("$.detail").value(DEFAULT_DETAIL))
            .andExpect(jsonPath("$.kind").value(DEFAULT_KIND.toString()))
            .andExpect(jsonPath("$.source").value(DEFAULT_SOURCE.toString()))
            .andExpect(jsonPath("$.authorId").value(DEFAULT_AUTHOR_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY));
    }

    @Test
    void getNonExistingActivityLog() throws Exception {
        // Get the activityLog
        restActivityLogMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingActivityLog() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();

        // Update the activityLog
        ActivityLog updatedActivityLog = activityLogRepository.findById(activityLog.getId()).orElseThrow();
        updatedActivityLog
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .loggedAt(UPDATED_LOGGED_AT)
            .summary(UPDATED_SUMMARY)
            .detail(UPDATED_DETAIL)
            .kind(UPDATED_KIND)
            .source(UPDATED_SOURCE)
            .authorId(UPDATED_AUTHOR_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restActivityLogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedActivityLog.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedActivityLog))
            )
            .andExpect(status().isOk());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
        ActivityLog testActivityLog = activityLogList.get(activityLogList.size() - 1);
        assertThat(testActivityLog.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testActivityLog.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testActivityLog.getLoggedAt()).isEqualTo(UPDATED_LOGGED_AT);
        assertThat(testActivityLog.getSummary()).isEqualTo(UPDATED_SUMMARY);
        assertThat(testActivityLog.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testActivityLog.getKind()).isEqualTo(UPDATED_KIND);
        assertThat(testActivityLog.getSource()).isEqualTo(UPDATED_SOURCE);
        assertThat(testActivityLog.getAuthorId()).isEqualTo(UPDATED_AUTHOR_ID);
        assertThat(testActivityLog.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testActivityLog.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
    }

    @Test
    void putNonExistingActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, activityLog.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(activityLog))
            )
            .andExpect(status().isBadRequest());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(activityLog))
            )
            .andExpect(status().isBadRequest());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(activityLog)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateActivityLogWithPatch() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();

        // Update the activityLog using partial update
        ActivityLog partialUpdatedActivityLog = new ActivityLog();
        partialUpdatedActivityLog.setId(activityLog.getId());

        partialUpdatedActivityLog.patientId(UPDATED_PATIENT_ID).caseId(UPDATED_CASE_ID).kind(UPDATED_KIND).createdBy(UPDATED_CREATED_BY);

        restActivityLogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedActivityLog.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedActivityLog))
            )
            .andExpect(status().isOk());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
        ActivityLog testActivityLog = activityLogList.get(activityLogList.size() - 1);
        assertThat(testActivityLog.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testActivityLog.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testActivityLog.getLoggedAt()).isEqualTo(DEFAULT_LOGGED_AT);
        assertThat(testActivityLog.getSummary()).isEqualTo(DEFAULT_SUMMARY);
        assertThat(testActivityLog.getDetail()).isEqualTo(DEFAULT_DETAIL);
        assertThat(testActivityLog.getKind()).isEqualTo(UPDATED_KIND);
        assertThat(testActivityLog.getSource()).isEqualTo(DEFAULT_SOURCE);
        assertThat(testActivityLog.getAuthorId()).isEqualTo(DEFAULT_AUTHOR_ID);
        assertThat(testActivityLog.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testActivityLog.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
    }

    @Test
    void fullUpdateActivityLogWithPatch() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();

        // Update the activityLog using partial update
        ActivityLog partialUpdatedActivityLog = new ActivityLog();
        partialUpdatedActivityLog.setId(activityLog.getId());

        partialUpdatedActivityLog
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .loggedAt(UPDATED_LOGGED_AT)
            .summary(UPDATED_SUMMARY)
            .detail(UPDATED_DETAIL)
            .kind(UPDATED_KIND)
            .source(UPDATED_SOURCE)
            .authorId(UPDATED_AUTHOR_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restActivityLogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedActivityLog.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedActivityLog))
            )
            .andExpect(status().isOk());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
        ActivityLog testActivityLog = activityLogList.get(activityLogList.size() - 1);
        assertThat(testActivityLog.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testActivityLog.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testActivityLog.getLoggedAt()).isEqualTo(UPDATED_LOGGED_AT);
        assertThat(testActivityLog.getSummary()).isEqualTo(UPDATED_SUMMARY);
        assertThat(testActivityLog.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testActivityLog.getKind()).isEqualTo(UPDATED_KIND);
        assertThat(testActivityLog.getSource()).isEqualTo(UPDATED_SOURCE);
        assertThat(testActivityLog.getAuthorId()).isEqualTo(UPDATED_AUTHOR_ID);
        assertThat(testActivityLog.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testActivityLog.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
    }

    @Test
    void patchNonExistingActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, activityLog.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(activityLog))
            )
            .andExpect(status().isBadRequest());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(activityLog))
            )
            .andExpect(status().isBadRequest());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamActivityLog() throws Exception {
        int databaseSizeBeforeUpdate = activityLogRepository.findAll().size();
        activityLog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restActivityLogMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(activityLog))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the ActivityLog in the database
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteActivityLog() throws Exception {
        // Initialize the database
        activityLogRepository.save(activityLog);

        int databaseSizeBeforeDelete = activityLogRepository.findAll().size();

        // Delete the activityLog
        restActivityLogMockMvc
            .perform(delete(ENTITY_API_URL_ID, activityLog.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<ActivityLog> activityLogList = activityLogRepository.findAll();
        assertThat(activityLogList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
