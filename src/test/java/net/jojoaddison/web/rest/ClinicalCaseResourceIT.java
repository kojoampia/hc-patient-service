package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ClinicalCaseAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.repository.ClinicalCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ClinicalCaseResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ClinicalCaseResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_OPENED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_OPENED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

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
    private ObjectMapper om;

    @Autowired
    private ClinicalCaseRepository clinicalCaseRepository;

    @Autowired
    private MockMvc restClinicalCaseMockMvc;

    private ClinicalCase clinicalCase;

    private ClinicalCase insertedClinicalCase;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ClinicalCase createEntity() {
        return new ClinicalCase()
            .patientId(DEFAULT_PATIENT_ID)
            .openedAt(DEFAULT_OPENED_AT)
            .brief(DEFAULT_BRIEF)
            .status(DEFAULT_STATUS)
            .symptoms(DEFAULT_SYMPTOMS)
            .diagnosis(DEFAULT_DIAGNOSIS)
            .assignedProfessionalId(DEFAULT_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(DEFAULT_ASSIGNED_ROSTER_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ClinicalCase createUpdatedEntity() {
        return new ClinicalCase()
            .patientId(UPDATED_PATIENT_ID)
            .openedAt(UPDATED_OPENED_AT)
            .brief(UPDATED_BRIEF)
            .status(UPDATED_STATUS)
            .symptoms(UPDATED_SYMPTOMS)
            .diagnosis(UPDATED_DIAGNOSIS)
            .assignedProfessionalId(UPDATED_ASSIGNED_PROFESSIONAL_ID)
            .assignedRosterId(UPDATED_ASSIGNED_ROSTER_ID);
    }

    @BeforeEach
    void initTest() {
        clinicalCase = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedClinicalCase != null) {
            clinicalCaseRepository.delete(insertedClinicalCase);
            insertedClinicalCase = null;
        }
    }

    @Test
    void createClinicalCase() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ClinicalCase
        var returnedClinicalCase = om.readValue(
            restClinicalCaseMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(clinicalCase)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ClinicalCase.class
        );

        // Validate the ClinicalCase in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertClinicalCaseUpdatableFieldsEquals(returnedClinicalCase, getPersistedClinicalCase(returnedClinicalCase));

        insertedClinicalCase = returnedClinicalCase;
    }

    @Test
    void createClinicalCaseWithExistingId() throws Exception {
        // Create the ClinicalCase with an existing ID
        clinicalCase.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restClinicalCaseMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(clinicalCase)))
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllClinicalCases() throws Exception {
        // Initialize the database
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        // Get all the clinicalCaseList
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(clinicalCase.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].openedAt").value(hasItem(DEFAULT_OPENED_AT.toString())))
            .andExpect(jsonPath("$.[*].brief").value(hasItem(DEFAULT_BRIEF)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].symptoms").value(hasItem(DEFAULT_SYMPTOMS)))
            .andExpect(jsonPath("$.[*].diagnosis").value(hasItem(DEFAULT_DIAGNOSIS)))
            .andExpect(jsonPath("$.[*].assignedProfessionalId").value(hasItem(DEFAULT_ASSIGNED_PROFESSIONAL_ID)))
            .andExpect(jsonPath("$.[*].assignedRosterId").value(hasItem(DEFAULT_ASSIGNED_ROSTER_ID)));
    }

    @Test
    void getClinicalCase() throws Exception {
        // Initialize the database
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        // Get the clinicalCase
        restClinicalCaseMockMvc
            .perform(get(ENTITY_API_URL_ID, clinicalCase.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(clinicalCase.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.openedAt").value(DEFAULT_OPENED_AT.toString()))
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
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the clinicalCase
        ClinicalCase updatedClinicalCase = clinicalCaseRepository.findById(clinicalCase.getId()).orElseThrow();
        updatedClinicalCase
            .patientId(UPDATED_PATIENT_ID)
            .openedAt(UPDATED_OPENED_AT)
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
                    .content(om.writeValueAsBytes(updatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedClinicalCaseToMatchAllProperties(updatedClinicalCase);
    }

    @Test
    void putNonExistingClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, clinicalCase.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(clinicalCase)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateClinicalCaseWithPatch() throws Exception {
        // Initialize the database
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the clinicalCase using partial update
        ClinicalCase partialUpdatedClinicalCase = new ClinicalCase();
        partialUpdatedClinicalCase.setId(clinicalCase.getId());

        partialUpdatedClinicalCase.brief(UPDATED_BRIEF).status(UPDATED_STATUS).symptoms(UPDATED_SYMPTOMS);

        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedClinicalCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertClinicalCaseUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedClinicalCase, clinicalCase),
            getPersistedClinicalCase(clinicalCase)
        );
    }

    @Test
    void fullUpdateClinicalCaseWithPatch() throws Exception {
        // Initialize the database
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the clinicalCase using partial update
        ClinicalCase partialUpdatedClinicalCase = new ClinicalCase();
        partialUpdatedClinicalCase.setId(clinicalCase.getId());

        partialUpdatedClinicalCase
            .patientId(UPDATED_PATIENT_ID)
            .openedAt(UPDATED_OPENED_AT)
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
                    .content(om.writeValueAsBytes(partialUpdatedClinicalCase))
            )
            .andExpect(status().isOk());

        // Validate the ClinicalCase in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertClinicalCaseUpdatableFieldsEquals(partialUpdatedClinicalCase, getPersistedClinicalCase(partialUpdatedClinicalCase));
    }

    @Test
    void patchNonExistingClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, clinicalCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(clinicalCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamClinicalCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        clinicalCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restClinicalCaseMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(clinicalCase)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ClinicalCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteClinicalCase() throws Exception {
        // Initialize the database
        insertedClinicalCase = clinicalCaseRepository.save(clinicalCase);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the clinicalCase
        restClinicalCaseMockMvc
            .perform(delete(ENTITY_API_URL_ID, clinicalCase.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return clinicalCaseRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected ClinicalCase getPersistedClinicalCase(ClinicalCase clinicalCase) {
        return clinicalCaseRepository.findById(clinicalCase.getId()).orElseThrow();
    }

    protected void assertPersistedClinicalCaseToMatchAllProperties(ClinicalCase expectedClinicalCase) {
        assertClinicalCaseAllPropertiesEquals(expectedClinicalCase, getPersistedClinicalCase(expectedClinicalCase));
    }

    protected void assertPersistedClinicalCaseToMatchUpdatableProperties(ClinicalCase expectedClinicalCase) {
        assertClinicalCaseAllUpdatablePropertiesEquals(expectedClinicalCase, getPersistedClinicalCase(expectedClinicalCase));
    }
}
