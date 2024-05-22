package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.HCDocument;
import net.jojoaddison.repository.HCDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link HCDocumentResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class HCDocumentResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/hc-documents";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private HCDocumentRepository hCDocumentRepository;

    @Autowired
    private MockMvc restHCDocumentMockMvc;

    private HCDocument hCDocument;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCDocument createEntity() {
        HCDocument hCDocument = new HCDocument().name(DEFAULT_NAME);
        return hCDocument;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCDocument createUpdatedEntity() {
        HCDocument hCDocument = new HCDocument().name(UPDATED_NAME);
        return hCDocument;
    }

    @BeforeEach
    public void initTest() {
        hCDocumentRepository.deleteAll();
        hCDocument = createEntity();
    }

    @Test
    void createHCDocument() throws Exception {
        int databaseSizeBeforeCreate = hCDocumentRepository.findAll().size();
        // Create the HCDocument
        restHCDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCDocument)))
            .andExpect(status().isCreated());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeCreate + 1);
        HCDocument testHCDocument = hCDocumentList.get(hCDocumentList.size() - 1);
        assertThat(testHCDocument.getName()).isEqualTo(DEFAULT_NAME);
    }

    @Test
    void createHCDocumentWithExistingId() throws Exception {
        // Create the HCDocument with an existing ID
        hCDocument.setId("existing_id");

        int databaseSizeBeforeCreate = hCDocumentRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHCDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCDocument)))
            .andExpect(status().isBadRequest());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllHCDocuments() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        // Get all the hCDocumentList
        restHCDocumentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(hCDocument.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)));
    }

    @Test
    void getHCDocument() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        // Get the hCDocument
        restHCDocumentMockMvc
            .perform(get(ENTITY_API_URL_ID, hCDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(hCDocument.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME));
    }

    @Test
    void getNonExistingHCDocument() throws Exception {
        // Get the hCDocument
        restHCDocumentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingHCDocument() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();

        // Update the hCDocument
        HCDocument updatedHCDocument = hCDocumentRepository.findById(hCDocument.getId()).orElseThrow();
        updatedHCDocument.name(UPDATED_NAME);

        restHCDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHCDocument.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedHCDocument))
            )
            .andExpect(status().isOk());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
        HCDocument testHCDocument = hCDocumentList.get(hCDocumentList.size() - 1);
        assertThat(testHCDocument.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    void putNonExistingHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, hCDocument.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCDocument)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateHCDocumentWithPatch() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();

        // Update the hCDocument using partial update
        HCDocument partialUpdatedHCDocument = new HCDocument();
        partialUpdatedHCDocument.setId(hCDocument.getId());

        restHCDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCDocument))
            )
            .andExpect(status().isOk());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
        HCDocument testHCDocument = hCDocumentList.get(hCDocumentList.size() - 1);
        assertThat(testHCDocument.getName()).isEqualTo(DEFAULT_NAME);
    }

    @Test
    void fullUpdateHCDocumentWithPatch() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();

        // Update the hCDocument using partial update
        HCDocument partialUpdatedHCDocument = new HCDocument();
        partialUpdatedHCDocument.setId(hCDocument.getId());

        partialUpdatedHCDocument.name(UPDATED_NAME);

        restHCDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCDocument))
            )
            .andExpect(status().isOk());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
        HCDocument testHCDocument = hCDocumentList.get(hCDocumentList.size() - 1);
        assertThat(testHCDocument.getName()).isEqualTo(UPDATED_NAME);
    }

    @Test
    void patchNonExistingHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, hCDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamHCDocument() throws Exception {
        int databaseSizeBeforeUpdate = hCDocumentRepository.findAll().size();
        hCDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(hCDocument))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCDocument in the database
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteHCDocument() throws Exception {
        // Initialize the database
        hCDocumentRepository.save(hCDocument);

        int databaseSizeBeforeDelete = hCDocumentRepository.findAll().size();

        // Delete the hCDocument
        restHCDocumentMockMvc
            .perform(delete(ENTITY_API_URL_ID, hCDocument.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<HCDocument> hCDocumentList = hCDocumentRepository.findAll();
        assertThat(hCDocumentList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
