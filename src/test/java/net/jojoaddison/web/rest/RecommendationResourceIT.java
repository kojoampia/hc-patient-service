package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.RecommendationAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.repository.RecommendationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link RecommendationResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class RecommendationResourceIT {

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final String DEFAULT_CATEGORY = "AAAAAAAAAA";
    private static final String UPDATED_CATEGORY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/recommendations";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private MockMvc restRecommendationMockMvc;

    private Recommendation recommendation;

    private Recommendation insertedRecommendation;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Recommendation createEntity() {
        return new Recommendation().label(DEFAULT_LABEL).category(DEFAULT_CATEGORY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Recommendation createUpdatedEntity() {
        return new Recommendation().label(UPDATED_LABEL).category(UPDATED_CATEGORY);
    }

    @BeforeEach
    void initTest() {
        recommendation = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedRecommendation != null) {
            recommendationRepository.delete(insertedRecommendation);
            insertedRecommendation = null;
        }
    }

    @Test
    void createRecommendation() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Recommendation
        var returnedRecommendation = om.readValue(
            restRecommendationMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(recommendation)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Recommendation.class
        );

        // Validate the Recommendation in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertRecommendationUpdatableFieldsEquals(returnedRecommendation, getPersistedRecommendation(returnedRecommendation));

        insertedRecommendation = returnedRecommendation;
    }

    @Test
    void createRecommendationWithExistingId() throws Exception {
        // Create the Recommendation with an existing ID
        recommendation.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restRecommendationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(recommendation)))
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllRecommendations() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        // Get all the recommendationList
        restRecommendationMockMvc
            .perform(get(ENTITY_API_URL))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(recommendation.getId())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY)));
    }

    @Test
    void getRecommendation() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        // Get the recommendation
        restRecommendationMockMvc
            .perform(get(ENTITY_API_URL_ID, recommendation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(recommendation.getId()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY));
    }

    @Test
    void getNonExistingRecommendation() throws Exception {
        // Get the recommendation
        restRecommendationMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingRecommendation() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the recommendation
        Recommendation updatedRecommendation = recommendationRepository.findById(recommendation.getId()).orElseThrow();
        updatedRecommendation.label(UPDATED_LABEL).category(UPDATED_CATEGORY);

        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedRecommendation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedRecommendationToMatchAllProperties(updatedRecommendation);
    }

    @Test
    void putNonExistingRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, recommendation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(recommendation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateRecommendationWithPatch() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the recommendation using partial update
        Recommendation partialUpdatedRecommendation = new Recommendation();
        partialUpdatedRecommendation.setId(recommendation.getId());

        partialUpdatedRecommendation.label(UPDATED_LABEL);

        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRecommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRecommendationUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedRecommendation, recommendation),
            getPersistedRecommendation(recommendation)
        );
    }

    @Test
    void fullUpdateRecommendationWithPatch() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the recommendation using partial update
        Recommendation partialUpdatedRecommendation = new Recommendation();
        partialUpdatedRecommendation.setId(recommendation.getId());

        partialUpdatedRecommendation.label(UPDATED_LABEL).category(UPDATED_CATEGORY);

        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRecommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRecommendationUpdatableFieldsEquals(partialUpdatedRecommendation, getPersistedRecommendation(partialUpdatedRecommendation));
    }

    @Test
    void patchNonExistingRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, recommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamRecommendation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(recommendation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Recommendation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteRecommendation() throws Exception {
        // Initialize the database
        insertedRecommendation = recommendationRepository.save(recommendation);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the recommendation
        restRecommendationMockMvc
            .perform(delete(ENTITY_API_URL_ID, recommendation.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return recommendationRepository.count();
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

    protected Recommendation getPersistedRecommendation(Recommendation recommendation) {
        return recommendationRepository.findById(recommendation.getId()).orElseThrow();
    }

    protected void assertPersistedRecommendationToMatchAllProperties(Recommendation expectedRecommendation) {
        assertRecommendationAllPropertiesEquals(expectedRecommendation, getPersistedRecommendation(expectedRecommendation));
    }

    protected void assertPersistedRecommendationToMatchUpdatableProperties(Recommendation expectedRecommendation) {
        assertRecommendationAllUpdatablePropertiesEquals(expectedRecommendation, getPersistedRecommendation(expectedRecommendation));
    }
}
