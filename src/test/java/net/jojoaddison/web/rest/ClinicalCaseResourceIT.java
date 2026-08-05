package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.service.ClinicalCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ClinicalCaseResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
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
class ClinicalCaseResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_CASE_NUMBER = 1;
    private static final Integer UPDATED_CASE_NUMBER = 2;

    private static final String DEFAULT_TITLE = "AAAAAAAAAA";
    private static final String UPDATED_TITLE = "BBBBBBBBBB";

    private static final Instant DEFAULT_OPENED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_OPENED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_CLOSED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CLOSED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_BRIEF = "AAAAAAAAAA";
    private static final String UPDATED_BRIEF = "BBBBBBBBBB";

    private static final CaseStatus DEFAULT_STATUS = CaseStatus.URGENT;
    private static final CaseStatus UPDATED_STATUS = CaseStatus.OPEN;

    private static final String DEFAULT_SYMPTOMS = "AAAAAAAAAA";
    private static final String UPDATED_SYMPTOMS = "BBBBBBBBBB";

    private static final String DEFAULT_DIAGNOSIS = "AAAAAAAAAA";
    private static final String UPDATED_DIAGNOSIS = "BBBBBBBBBB";

    private static final String DEFAULT_ASSIGNED_PROFESSIONAL_ID = "AAAAAAAAAA";
    private static final String UPDATED_ASSIGNED_PROFESSIONAL_ID = "BBBBBBBBBB";

    private static final String DEFAULT_ASSIGNED_ROSTER_ID = "AAAAAAAAAA";
    private static final String UPDATED_ASSIGNED_ROSTER_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/clinical-cases";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ClinicalCaseRepository clinicalCaseRepository;

    @Mock
    private ClinicalCaseRepository clinicalCaseRepositoryMock;

    @Mock
    private ClinicalCaseService clinicalCaseServiceMock;

    @Autowired
    private MockMvc restClinicalCaseMockMvc;

    private ClinicalCase clinicalCase;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ClinicalCase createEntity() {
        ClinicalCase clinicalCase = new ClinicalCase()
            .patientId(DEFAULT_PATIENT_ID)
            .caseNumber(DEFAULT_CASE_NUMBER)
            .title(DEFAULT_TITLE)
            .openedAt(DEFAULT_OPENED_AT)
            .closedAt(DEFAULT_CLOSED_AT)
            .brief(DEFAULT_BRIEF)
            .status(DEFAULT_STATUS)
            .symptoms(DEFAULT_SYMPTOMS)
            .diagnosis(DEFAULT_DIAGNOSIS)
            .assignedProfessionalId(DEFAULT_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(DEFAULT_ASSIGNED_ROSTER_ID);
        return clinicalCase;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ClinicalCase createUpdatedEntity() {
        ClinicalCase clinicalCase = new ClinicalCase()
            .patientId(UPDATED_PATIENT_ID)
            .caseNumber(UPDATED_CASE_NUMBER)
            .title(UPDATED_TITLE)
            .openedAt(UPDATED_OPENED_AT)
            .closedAt(UPDATED_CLOSED_AT)
            .brief(UPDATED_BRIEF)
            .status(UPDATED_STATUS)
            .symptoms(UPDATED_SYMPTOMS)
            .diagnosis(UPDATED_DIAGNOSIS)
            .assignedProfessionalId(UPDATED_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(UPDATED_ASSIGNED_ROSTER_ID);
        return clinicalCase;
    }

    @BeforeEach
    public void initTest() {
        clinicalCaseRepository.deleteAll();
        clinicalCase = createEntity();
    }

    @Test
    void createClinicalCase() throws Exception {
        int databaseSizeBeforeCreate = clinicalCaseRepository.findAll().size();
        // Create the ClinicalCase
        restClinicalCaseMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(clinicalCase)))
            .andExpect(status().isCreated());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeCreate + 1);
        ClinicalCase testClinicalCase = clinicalCaseList.get(clinicalCaseList.size() - 1);
        assertThat(testClinicalCase.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testClinicalCase.getCaseNumber()).isEqualTo(DEFAULT_CASE_NUMBER);
        assertThat(testClinicalCase.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testClinicalCase.getOpenedAt()).isEqualTo(DEFAULT_OPENED_AT);
        assertThat(testClinicalCase.getClosedAt()).isEqualTo(DEFAULT_CLOSED_AT);
        assertThat(testClinicalCase.getBrief()).isEqualTo(DEFAULT_BRIEF);
        assertThat(testClinicalCase.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testClinicalCase.getSymptoms()).isEqualTo(DEFAULT_SYMPTOMS);
        assertThat(testClinicalCase.getDiagnosis()).isEqualTo(DEFAULT_DIAGNOSIS);
        assertThat(testClinicalCase.getAssignedProfessionalId()).isEqualTo(DEFAULT_ASSIGNED_PROFESSIONAL_ID);
        assertThat(testClinicalCase.getAssignedRosterId()).isEqualTo(DEFAULT_ASSIGNED_ROSTER_ID);
    }

    @Test
    void createClinicalCaseWithExistingId() throws Exception {
        // Create the ClinicalCase with an existing ID
        clinicalCase.setId("existing_id");

        int databaseSizeBeforeCreate = clinicalCaseRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restClinicalCaseMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(clinicalCase)))
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllClinicalCases() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        // Get all the clinicalCaseList
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(clinicalCase.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].caseNumber").value(hasItem(DEFAULT_CASE_NUMBER)))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE)))
            .andExpect(jsonPath("$.[*].openedAt").value(hasItem(DEFAULT_OPENED_AT.toString())))
            .andExpect(jsonPath("$.[*].closedAt").value(hasItem(DEFAULT_CLOSED_AT.toString())))
            .andExpect(jsonPath("$.[*].brief").value(hasItem(DEFAULT_BRIEF)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].symptoms").value(hasItem(DEFAULT_SYMPTOMS)))
            .andExpect(jsonPath("$.[*].diagnosis").value(hasItem(DEFAULT_DIAGNOSIS)))
            .andExpect(jsonPath("$.[*].assignedProfessionalId").value(hasItem(DEFAULT_ASSIGNED_PROFESSIONAL_ID)))
            .andExpect(jsonPath("$.[*].assignedRosterId").value(hasItem(DEFAULT_ASSIGNED_ROSTER_ID)));
    }

    @Test
    void getAllClinicalCasesByPatientId() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        // The patient's own records come back
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(clinicalCase.getId())));

        // Another patient's id returns nothing rather than everything
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllClinicalCasesWithEagerRelationshipsIsEnabled() throws Exception {
        when(clinicalCaseServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restClinicalCaseMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(clinicalCaseServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllClinicalCasesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(clinicalCaseServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restClinicalCaseMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(clinicalCaseRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getClinicalCase() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        // Get the clinicalCase
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL_ID, clinicalCase.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(clinicalCase.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.caseNumber").value(DEFAULT_CASE_NUMBER))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE))
            .andExpect(jsonPath("$.openedAt").value(DEFAULT_OPENED_AT.toString()))
            .andExpect(jsonPath("$.closedAt").value(DEFAULT_CLOSED_AT.toString()))
            .andExpect(jsonPath("$.brief").value(DEFAULT_BRIEF))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.symptoms").value(DEFAULT_SYMPTOMS))
            .andExpect(jsonPath("$.diagnosis").value(DEFAULT_DIAGNOSIS))
            .andExpect(jsonPath("$.assignedProfessionalId").value(DEFAULT_ASSIGNED_PROFESSIONAL_ID))
            .andExpect(jsonPath("$.assignedRosterId").value(DEFAULT_ASSIGNED_ROSTER_ID));
    }

    @Test
    void getNonExistingClinicalCase() throws Exception {
        // Get the clinicalCase
        restClinicalCaseMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingClinicalCase() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();

        // Update the clinicalCase
        ClinicalCase updatedClinicalCase = clinicalCaseRepository.findById(clinicalCase.getId()).orElseThrow();
        updatedClinicalCase
            .patientId(UPDATED_PATIENT_ID)
            .caseNumber(UPDATED_CASE_NUMBER)
            .title(UPDATED_TITLE)
            .openedAt(UPDATED_OPENED_AT)
            .closedAt(UPDATED_CLOSED_AT)
            .brief(UPDATED_BRIEF)
            .status(UPDATED_STATUS)
            .symptoms(UPDATED_SYMPTOMS)
            .diagnosis(UPDATED_DIAGNOSIS)
            .assignedProfessionalId(UPDATED_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(UPDATED_ASSIGNED_ROSTER_ID);

        restClinicalCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedClinicalCase.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
        ClinicalCase testClinicalCase = clinicalCaseList.get(clinicalCaseList.size() - 1);
        assertThat(testClinicalCase.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testClinicalCase.getCaseNumber()).isEqualTo(UPDATED_CASE_NUMBER);
        assertThat(testClinicalCase.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testClinicalCase.getOpenedAt()).isEqualTo(UPDATED_OPENED_AT);
        assertThat(testClinicalCase.getClosedAt()).isEqualTo(UPDATED_CLOSED_AT);
        assertThat(testClinicalCase.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testClinicalCase.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testClinicalCase.getSymptoms()).isEqualTo(UPDATED_SYMPTOMS);
        assertThat(testClinicalCase.getDiagnosis()).isEqualTo(UPDATED_DIAGNOSIS);
        assertThat(testClinicalCase.getAssignedProfessionalId()).isEqualTo(UPDATED_ASSIGNED_PROFESSIONAL_ID);
        assertThat(testClinicalCase.getAssignedRosterId()).isEqualTo(UPDATED_ASSIGNED_ROSTER_ID);
    }

    @Test
    void putNonExistingClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, clinicalCase.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(clinicalCase)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateClinicalCaseWithPatch() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();

        // Update the clinicalCase using partial update
        ClinicalCase partialUpdatedClinicalCase = new ClinicalCase();
        partialUpdatedClinicalCase.setId(clinicalCase.getId());

        partialUpdatedClinicalCase
            .caseNumber(UPDATED_CASE_NUMBER)
            .title(UPDATED_TITLE)
            .brief(UPDATED_BRIEF)
            .status(UPDATED_STATUS)
            .symptoms(UPDATED_SYMPTOMS)
            .diagnosis(UPDATED_DIAGNOSIS);

        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedClinicalCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
        ClinicalCase testClinicalCase = clinicalCaseList.get(clinicalCaseList.size() - 1);
        assertThat(testClinicalCase.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testClinicalCase.getCaseNumber()).isEqualTo(UPDATED_CASE_NUMBER);
        assertThat(testClinicalCase.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testClinicalCase.getOpenedAt()).isEqualTo(DEFAULT_OPENED_AT);
        assertThat(testClinicalCase.getClosedAt()).isEqualTo(DEFAULT_CLOSED_AT);
        assertThat(testClinicalCase.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testClinicalCase.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testClinicalCase.getSymptoms()).isEqualTo(UPDATED_SYMPTOMS);
        assertThat(testClinicalCase.getDiagnosis()).isEqualTo(UPDATED_DIAGNOSIS);
        assertThat(testClinicalCase.getAssignedProfessionalId()).isEqualTo(DEFAULT_ASSIGNED_PROFESSIONAL_ID);
        assertThat(testClinicalCase.getAssignedRosterId()).isEqualTo(DEFAULT_ASSIGNED_ROSTER_ID);
    }

    @Test
    void fullUpdateClinicalCaseWithPatch() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();

        // Update the clinicalCase using partial update
        ClinicalCase partialUpdatedClinicalCase = new ClinicalCase();
        partialUpdatedClinicalCase.setId(clinicalCase.getId());

        partialUpdatedClinicalCase
            .patientId(UPDATED_PATIENT_ID)
            .caseNumber(UPDATED_CASE_NUMBER)
            .title(UPDATED_TITLE)
            .openedAt(UPDATED_OPENED_AT)
            .closedAt(UPDATED_CLOSED_AT)
            .brief(UPDATED_BRIEF)
            .status(UPDATED_STATUS)
            .symptoms(UPDATED_SYMPTOMS)
            .diagnosis(UPDATED_DIAGNOSIS)
            .assignedProfessionalId(UPDATED_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(UPDATED_ASSIGNED_ROSTER_ID);

        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedClinicalCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
        ClinicalCase testClinicalCase = clinicalCaseList.get(clinicalCaseList.size() - 1);
        assertThat(testClinicalCase.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testClinicalCase.getCaseNumber()).isEqualTo(UPDATED_CASE_NUMBER);
        assertThat(testClinicalCase.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testClinicalCase.getOpenedAt()).isEqualTo(UPDATED_OPENED_AT);
        assertThat(testClinicalCase.getClosedAt()).isEqualTo(UPDATED_CLOSED_AT);
        assertThat(testClinicalCase.getBrief()).isEqualTo(UPDATED_BRIEF);
        assertThat(testClinicalCase.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testClinicalCase.getSymptoms()).isEqualTo(UPDATED_SYMPTOMS);
        assertThat(testClinicalCase.getDiagnosis()).isEqualTo(UPDATED_DIAGNOSIS);
        assertThat(testClinicalCase.getAssignedProfessionalId()).isEqualTo(UPDATED_ASSIGNED_PROFESSIONAL_ID);
        assertThat(testClinicalCase.getAssignedRosterId()).isEqualTo(UPDATED_ASSIGNED_ROSTER_ID);
    }

    @Test
    void patchNonExistingClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, clinicalCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamClinicalCase() throws Exception {
        int databaseSizeBeforeUpdate = clinicalCaseRepository.findAll().size();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(clinicalCase))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the ClinicalCase in the database
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteClinicalCase() throws Exception {
        // Initialize the database
        clinicalCaseRepository.save(clinicalCase);

        int databaseSizeBeforeDelete = clinicalCaseRepository.findAll().size();

        // Delete the clinicalCase
        restClinicalCaseMockMvc
            .perform(delete(ENTITY_API_URL_ID, clinicalCase.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<ClinicalCase> clinicalCaseList = clinicalCaseRepository.findAll();
        assertThat(clinicalCaseList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
