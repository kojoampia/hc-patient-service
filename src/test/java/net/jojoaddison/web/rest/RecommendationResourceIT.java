package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    private RecommendationRepository recommendationRepository;

    @Autowired
    private MockMvc restRecommendationMockMvc;

    private Recommendation recommendation;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Recommendation createEntity() {
        Recommendation recommendation = new Recommendation().label(DEFAULT_LABEL).category(DEFAULT_CATEGORY);
        return recommendation;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Recommendation createUpdatedEntity() {
        Recommendation recommendation = new Recommendation().label(UPDATED_LABEL).category(UPDATED_CATEGORY);
        return recommendation;
    }

    @BeforeEach
    public void initTest() {
        recommendationRepository.deleteAll();
        recommendation = createEntity();
    }

    @Test
    void createRecommendation() throws Exception {
        int databaseSizeBeforeCreate = recommendationRepository.findAll().size();
        // Create the Recommendation
        restRecommendationMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isCreated());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeCreate + 1);
        Recommendation testRecommendation = recommendationList.get(recommendationList.size() - 1);
        assertThat(testRecommendation.getLabel()).isEqualTo(DEFAULT_LABEL);
        assertThat(testRecommendation.getCategory()).isEqualTo(DEFAULT_CATEGORY);
    }

    @Test
    void createRecommendationWithExistingId() throws Exception {
        // Create the Recommendation with an existing ID
        recommendation.setId("existing_id");

        int databaseSizeBeforeCreate = recommendationRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restRecommendationMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllRecommendations() throws Exception {
        // Initialize the database
        recommendationRepository.save(recommendation);

        // Get all the recommendationList
        restRecommendationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(recommendation.getId())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY)));
    }

    @Test
    void getRecommendation() throws Exception {
        // Initialize the database
        recommendationRepository.save(recommendation);

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
        recommendationRepository.save(recommendation);

        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();

        // Update the recommendation
        Recommendation updatedRecommendation = recommendationRepository.findById(recommendation.getId()).orElseThrow();
        updatedRecommendation.label(UPDATED_LABEL).category(UPDATED_CATEGORY);

        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedRecommendation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
        Recommendation testRecommendation = recommendationList.get(recommendationList.size() - 1);
        assertThat(testRecommendation.getLabel()).isEqualTo(UPDATED_LABEL);
        assertThat(testRecommendation.getCategory()).isEqualTo(UPDATED_CATEGORY);
    }

    @Test
    void putNonExistingRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, recommendation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(recommendation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateRecommendationWithPatch() throws Exception {
        // Initialize the database
        recommendationRepository.save(recommendation);

        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();

        // Update the recommendation using partial update
        Recommendation partialUpdatedRecommendation = new Recommendation();
        partialUpdatedRecommendation.setId(recommendation.getId());

        partialUpdatedRecommendation.category(UPDATED_CATEGORY);

        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRecommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
        Recommendation testRecommendation = recommendationList.get(recommendationList.size() - 1);
        assertThat(testRecommendation.getLabel()).isEqualTo(DEFAULT_LABEL);
        assertThat(testRecommendation.getCategory()).isEqualTo(UPDATED_CATEGORY);
    }

    @Test
    void fullUpdateRecommendationWithPatch() throws Exception {
        // Initialize the database
        recommendationRepository.save(recommendation);

        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();

        // Update the recommendation using partial update
        Recommendation partialUpdatedRecommendation = new Recommendation();
        partialUpdatedRecommendation.setId(recommendation.getId());

        partialUpdatedRecommendation.label(UPDATED_LABEL).category(UPDATED_CATEGORY);

        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRecommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedRecommendation))
            )
            .andExpect(status().isOk());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
        Recommendation testRecommendation = recommendationList.get(recommendationList.size() - 1);
        assertThat(testRecommendation.getLabel()).isEqualTo(UPDATED_LABEL);
        assertThat(testRecommendation.getCategory()).isEqualTo(UPDATED_CATEGORY);
    }

    @Test
    void patchNonExistingRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, recommendation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamRecommendation() throws Exception {
        int databaseSizeBeforeUpdate = recommendationRepository.findAll().size();
        recommendation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRecommendationMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(recommendation))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Recommendation in the database
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteRecommendation() throws Exception {
        // Initialize the database
        recommendationRepository.save(recommendation);

        int databaseSizeBeforeDelete = recommendationRepository.findAll().size();

        // Delete the recommendation
        restRecommendationMockMvc
            .perform(delete(ENTITY_API_URL_ID, recommendation.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Recommendation> recommendationList = recommendationRepository.findAll();
        assertThat(recommendationList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
