package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PersonalDocument;
import net.jojoaddison.repository.PersonalDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PersonalDocumentResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class PersonalDocumentResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CATEGORY = "AAAAAAAAAA";
    private static final String UPDATED_CATEGORY = "BBBBBBBBBB";

    private static final String DEFAULT_URL = "AAAAAAAAAA";
    private static final String UPDATED_URL = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_ISSUED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_ISSUED_ON = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_EXPIRES_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_EXPIRES_ON = LocalDate.now(ZoneId.systemDefault());

    private static final String ENTITY_API_URL = "/api/personal-documents";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private PersonalDocumentRepository personalDocumentRepository;

    @Autowired
    private MockMvc restPersonalDocumentMockMvc;

    private PersonalDocument personalDocument;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PersonalDocument createEntity() {
        PersonalDocument personalDocument = new PersonalDocument()
            .name(DEFAULT_NAME)
            .category(DEFAULT_CATEGORY)
            .url(DEFAULT_URL)
            .patientId(DEFAULT_PATIENT_ID)
            .issuedOn(DEFAULT_ISSUED_ON)
            .expiresOn(DEFAULT_EXPIRES_ON);
        return personalDocument;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PersonalDocument createUpdatedEntity() {
        PersonalDocument personalDocument = new PersonalDocument()
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .url(UPDATED_URL)
            .patientId(UPDATED_PATIENT_ID)
            .issuedOn(UPDATED_ISSUED_ON)
            .expiresOn(UPDATED_EXPIRES_ON);
        return personalDocument;
    }

    @BeforeEach
    public void initTest() {
        personalDocumentRepository.deleteAll();
        personalDocument = createEntity();
    }

    @Test
    void createPersonalDocument() throws Exception {
        int databaseSizeBeforeCreate = personalDocumentRepository.findAll().size();
        // Create the PersonalDocument
        restPersonalDocumentMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isCreated());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeCreate + 1);
        PersonalDocument testPersonalDocument = personalDocumentList.get(personalDocumentList.size() - 1);
        assertThat(testPersonalDocument.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testPersonalDocument.getCategory()).isEqualTo(DEFAULT_CATEGORY);
        assertThat(testPersonalDocument.getUrl()).isEqualTo(DEFAULT_URL);
        assertThat(testPersonalDocument.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testPersonalDocument.getIssuedOn()).isEqualTo(DEFAULT_ISSUED_ON);
        assertThat(testPersonalDocument.getExpiresOn()).isEqualTo(DEFAULT_EXPIRES_ON);
    }

    @Test
    void createPersonalDocumentWithExistingId() throws Exception {
        // Create the PersonalDocument with an existing ID
        personalDocument.setId("existing_id");

        int databaseSizeBeforeCreate = personalDocumentRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPersonalDocumentMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllPersonalDocuments() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        // Get all the personalDocumentList
        restPersonalDocumentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(personalDocument.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY)))
            .andExpect(jsonPath("$.[*].url").value(hasItem(DEFAULT_URL)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].issuedOn").value(hasItem(DEFAULT_ISSUED_ON.toString())))
            .andExpect(jsonPath("$.[*].expiresOn").value(hasItem(DEFAULT_EXPIRES_ON.toString())));
    }

    @Test
    void getAllPersonalDocumentsByPatientId() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        // The patient's own records come back
        restPersonalDocumentMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(personalDocument.getId())));

        // Another patient's id returns nothing rather than everything
        restPersonalDocumentMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getPersonalDocument() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        // Get the personalDocument
        restPersonalDocumentMockMvc
            .perform(get(ENTITY_API_URL_ID, personalDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(personalDocument.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY))
            .andExpect(jsonPath("$.url").value(DEFAULT_URL))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.issuedOn").value(DEFAULT_ISSUED_ON.toString()))
            .andExpect(jsonPath("$.expiresOn").value(DEFAULT_EXPIRES_ON.toString()));
    }

    @Test
    void getNonExistingPersonalDocument() throws Exception {
        // Get the personalDocument
        restPersonalDocumentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPersonalDocument() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();

        // Update the personalDocument
        PersonalDocument updatedPersonalDocument = personalDocumentRepository.findById(personalDocument.getId()).orElseThrow();
        updatedPersonalDocument
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .url(UPDATED_URL)
            .patientId(UPDATED_PATIENT_ID)
            .issuedOn(UPDATED_ISSUED_ON)
            .expiresOn(UPDATED_EXPIRES_ON);

        restPersonalDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedPersonalDocument.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedPersonalDocument))
            )
            .andExpect(status().isOk());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
        PersonalDocument testPersonalDocument = personalDocumentList.get(personalDocumentList.size() - 1);
        assertThat(testPersonalDocument.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testPersonalDocument.getCategory()).isEqualTo(UPDATED_CATEGORY);
        assertThat(testPersonalDocument.getUrl()).isEqualTo(UPDATED_URL);
        assertThat(testPersonalDocument.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testPersonalDocument.getIssuedOn()).isEqualTo(UPDATED_ISSUED_ON);
        assertThat(testPersonalDocument.getExpiresOn()).isEqualTo(UPDATED_EXPIRES_ON);
    }

    @Test
    void putNonExistingPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, personalDocument.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePersonalDocumentWithPatch() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();

        // Update the personalDocument using partial update
        PersonalDocument partialUpdatedPersonalDocument = new PersonalDocument();
        partialUpdatedPersonalDocument.setId(personalDocument.getId());

        partialUpdatedPersonalDocument.category(UPDATED_CATEGORY).patientId(UPDATED_PATIENT_ID).issuedOn(UPDATED_ISSUED_ON);

        restPersonalDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPersonalDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedPersonalDocument))
            )
            .andExpect(status().isOk());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
        PersonalDocument testPersonalDocument = personalDocumentList.get(personalDocumentList.size() - 1);
        assertThat(testPersonalDocument.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testPersonalDocument.getCategory()).isEqualTo(UPDATED_CATEGORY);
        assertThat(testPersonalDocument.getUrl()).isEqualTo(DEFAULT_URL);
        assertThat(testPersonalDocument.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testPersonalDocument.getIssuedOn()).isEqualTo(UPDATED_ISSUED_ON);
        assertThat(testPersonalDocument.getExpiresOn()).isEqualTo(DEFAULT_EXPIRES_ON);
    }

    @Test
    void fullUpdatePersonalDocumentWithPatch() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();

        // Update the personalDocument using partial update
        PersonalDocument partialUpdatedPersonalDocument = new PersonalDocument();
        partialUpdatedPersonalDocument.setId(personalDocument.getId());

        partialUpdatedPersonalDocument
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .url(UPDATED_URL)
            .patientId(UPDATED_PATIENT_ID)
            .issuedOn(UPDATED_ISSUED_ON)
            .expiresOn(UPDATED_EXPIRES_ON);

        restPersonalDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPersonalDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedPersonalDocument))
            )
            .andExpect(status().isOk());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
        PersonalDocument testPersonalDocument = personalDocumentList.get(personalDocumentList.size() - 1);
        assertThat(testPersonalDocument.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testPersonalDocument.getCategory()).isEqualTo(UPDATED_CATEGORY);
        assertThat(testPersonalDocument.getUrl()).isEqualTo(UPDATED_URL);
        assertThat(testPersonalDocument.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testPersonalDocument.getIssuedOn()).isEqualTo(UPDATED_ISSUED_ON);
        assertThat(testPersonalDocument.getExpiresOn()).isEqualTo(UPDATED_EXPIRES_ON);
    }

    @Test
    void patchNonExistingPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, personalDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isBadRequest());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPersonalDocument() throws Exception {
        int databaseSizeBeforeUpdate = personalDocumentRepository.findAll().size();
        personalDocument.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPersonalDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL)
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(personalDocument))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the PersonalDocument in the database
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePersonalDocument() throws Exception {
        // Initialize the database
        personalDocumentRepository.save(personalDocument);

        int databaseSizeBeforeDelete = personalDocumentRepository.findAll().size();

        // Delete the personalDocument
        restPersonalDocumentMockMvc
            .perform(delete(ENTITY_API_URL_ID, personalDocument.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<PersonalDocument> personalDocumentList = personalDocumentRepository.findAll();
        assertThat(personalDocumentList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
