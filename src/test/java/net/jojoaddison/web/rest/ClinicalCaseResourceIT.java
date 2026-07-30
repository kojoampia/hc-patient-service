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
import net.jojoaddison.domain.enumeration.CaseCategory;
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

    private static final String DEFAULT_SYMPTOMS = "AAAAAAAAAA";
    private static final String UPDATED_SYMPTOMS = "BBBBBBBBBB";

    private static final String DEFAULT_DIAGNOSES = "AAAAAAAAAA";
    private static final String UPDATED_DIAGNOSES = "BBBBBBBBBB";

    private static final String DEFAULT_RECOMMENDATIONS = "AAAAAAAAAA";
    private static final String UPDATED_RECOMMENDATIONS = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final Instant DEFAULT_MODIFIED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_MODIFIED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final CaseStatus DEFAULT_STATUS = CaseStatus.URGENT;
    private static final CaseStatus UPDATED_STATUS = CaseStatus.OPEN;

    private static final Instant DEFAULT_OPEN_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_OPEN_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_CLOSE_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CLOSE_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final CaseCategory DEFAULT_CATEGORY = CaseCategory.ROUTINE;
    private static final CaseCategory UPDATED_CATEGORY = CaseCategory.FOLLOW_UP;

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
            .symptoms(DEFAULT_SYMPTOMS)
            .diagnoses(DEFAULT_DIAGNOSES)
            .recommendations(DEFAULT_RECOMMENDATIONS)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY)
            .status(DEFAULT_STATUS)
            .openDate(DEFAULT_OPEN_DATE)
            .closeDate(DEFAULT_CLOSE_DATE)
            .category(DEFAULT_CATEGORY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ClinicalCase createUpdatedEntity() {
        return new ClinicalCase()
            .symptoms(UPDATED_SYMPTOMS)
            .diagnoses(UPDATED_DIAGNOSES)
            .recommendations(UPDATED_RECOMMENDATIONS)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .status(UPDATED_STATUS)
            .openDate(UPDATED_OPEN_DATE)
            .closeDate(UPDATED_CLOSE_DATE)
            .category(UPDATED_CATEGORY);
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
            .andExpect(jsonPath("$.[*].symptoms").value(hasItem(DEFAULT_SYMPTOMS)))
            .andExpect(jsonPath("$.[*].diagnoses").value(hasItem(DEFAULT_DIAGNOSES)))
            .andExpect(jsonPath("$.[*].recommendations").value(hasItem(DEFAULT_RECOMMENDATIONS)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].openDate").value(hasItem(DEFAULT_OPEN_DATE.toString())))
            .andExpect(jsonPath("$.[*].closeDate").value(hasItem(DEFAULT_CLOSE_DATE.toString())))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY.toString())));
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
            .andExpect(jsonPath("$.symptoms").value(DEFAULT_SYMPTOMS))
            .andExpect(jsonPath("$.diagnoses").value(DEFAULT_DIAGNOSES))
            .andExpect(jsonPath("$.recommendations").value(DEFAULT_RECOMMENDATIONS))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.openDate").value(DEFAULT_OPEN_DATE.toString()))
            .andExpect(jsonPath("$.closeDate").value(DEFAULT_CLOSE_DATE.toString()))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY.toString()));
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
            .symptoms(UPDATED_SYMPTOMS)
            .diagnoses(UPDATED_DIAGNOSES)
            .recommendations(UPDATED_RECOMMENDATIONS)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .status(UPDATED_STATUS)
            .openDate(UPDATED_OPEN_DATE)
            .closeDate(UPDATED_CLOSE_DATE)
            .category(UPDATED_CATEGORY);

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

        partialUpdatedClinicalCase
            .symptoms(UPDATED_SYMPTOMS)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .status(UPDATED_STATUS)
            .openDate(UPDATED_OPEN_DATE);

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
            .symptoms(UPDATED_SYMPTOMS)
            .diagnoses(UPDATED_DIAGNOSES)
            .recommendations(UPDATED_RECOMMENDATIONS)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .status(UPDATED_STATUS)
            .openDate(UPDATED_OPEN_DATE)
            .closeDate(UPDATED_CLOSE_DATE)
            .category(UPDATED_CATEGORY);

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
