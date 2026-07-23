package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.MedCaseAsserts.*;
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
import net.jojoaddison.domain.MedCase;
import net.jojoaddison.domain.enumeration.CaseCategory;
import net.jojoaddison.domain.enumeration.CaseStatus;
import net.jojoaddison.repository.MedCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link MedCaseResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class MedCaseResourceIT {

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

    private static final String ENTITY_API_URL = "/api/med-cases";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MedCaseRepository medCaseRepository;

    @Autowired
    private MockMvc restMedCaseMockMvc;

    private MedCase medCase;

    private MedCase insertedMedCase;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static MedCase createEntity() {
        return new MedCase()
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
    public static MedCase createUpdatedEntity() {
        return new MedCase()
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
        medCase = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedMedCase != null) {
            medCaseRepository.delete(insertedMedCase);
            insertedMedCase = null;
        }
    }

    @Test
    void createMedCase() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the MedCase
        var returnedMedCase = om.readValue(
            restMedCaseMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medCase)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            MedCase.class
        );

        // Validate the MedCase in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertMedCaseUpdatableFieldsEquals(returnedMedCase, getPersistedMedCase(returnedMedCase));

        insertedMedCase = returnedMedCase;
    }

    @Test
    void createMedCaseWithExistingId() throws Exception {
        // Create the MedCase with an existing ID
        medCase.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMedCaseMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medCase)))
            .andExpect(status().isBadRequest());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMedCases() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        // Get all the medCaseList
        restMedCaseMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(medCase.getId())))
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
    void getMedCase() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        // Get the medCase
        restMedCaseMockMvc
            .perform(get(ENTITY_API_URL_ID, medCase.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(medCase.getId()))
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
    void getNonExistingMedCase() throws Exception {
        // Get the medCase
        restMedCaseMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingMedCase() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medCase
        MedCase updatedMedCase = medCaseRepository.findById(medCase.getId()).orElseThrow();
        updatedMedCase
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

        restMedCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedMedCase.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedMedCase))
            )
            .andExpect(status().isOk());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedMedCaseToMatchAllProperties(updatedMedCase);
    }

    @Test
    void putNonExistingMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(put(ENTITY_API_URL_ID, medCase.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medCase)))
            .andExpect(status().isBadRequest());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(medCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medCase)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMedCaseWithPatch() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medCase using partial update
        MedCase partialUpdatedMedCase = new MedCase();
        partialUpdatedMedCase.setId(medCase.getId());

        partialUpdatedMedCase
            .symptoms(UPDATED_SYMPTOMS)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .status(UPDATED_STATUS)
            .openDate(UPDATED_OPEN_DATE);

        restMedCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMedCase))
            )
            .andExpect(status().isOk());

        // Validate the MedCase in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMedCaseUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedMedCase, medCase), getPersistedMedCase(medCase));
    }

    @Test
    void fullUpdateMedCaseWithPatch() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medCase using partial update
        MedCase partialUpdatedMedCase = new MedCase();
        partialUpdatedMedCase.setId(medCase.getId());

        partialUpdatedMedCase
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

        restMedCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedCase.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMedCase))
            )
            .andExpect(status().isOk());

        // Validate the MedCase in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMedCaseUpdatableFieldsEquals(partialUpdatedMedCase, getPersistedMedCase(partialUpdatedMedCase));
    }

    @Test
    void patchNonExistingMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, medCase.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(medCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(medCase))
            )
            .andExpect(status().isBadRequest());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMedCase() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medCase.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedCaseMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(medCase)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the MedCase in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMedCase() throws Exception {
        // Initialize the database
        insertedMedCase = medCaseRepository.save(medCase);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the medCase
        restMedCaseMockMvc
            .perform(delete(ENTITY_API_URL_ID, medCase.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return medCaseRepository.count();
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

    protected MedCase getPersistedMedCase(MedCase medCase) {
        return medCaseRepository.findById(medCase.getId()).orElseThrow();
    }

    protected void assertPersistedMedCaseToMatchAllProperties(MedCase expectedMedCase) {
        assertMedCaseAllPropertiesEquals(expectedMedCase, getPersistedMedCase(expectedMedCase));
    }

    protected void assertPersistedMedCaseToMatchUpdatableProperties(MedCase expectedMedCase) {
        assertMedCaseAllUpdatablePropertiesEquals(expectedMedCase, getPersistedMedCase(expectedMedCase));
    }
}
