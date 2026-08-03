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
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.enumeration.AllergyCategory;
import net.jojoaddison.domain.enumeration.AllergySeverity;
import net.jojoaddison.repository.AllergyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link AllergyResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class AllergyResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final AllergyCategory DEFAULT_CATEGORY = AllergyCategory.MEDICATION;
    private static final AllergyCategory UPDATED_CATEGORY = AllergyCategory.FOOD;

    private static final AllergySeverity DEFAULT_SEVERITY = AllergySeverity.MILD;
    private static final AllergySeverity UPDATED_SEVERITY = AllergySeverity.MODERATE;

    private static final String DEFAULT_REACTION = "AAAAAAAAAA";
    private static final String UPDATED_REACTION = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_NOTED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_NOTED_ON = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_NOTED_BY_ID = "AAAAAAAAAA";
    private static final String UPDATED_NOTED_BY_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/allergies";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private AllergyRepository allergyRepository;

    @Autowired
    private MockMvc restAllergyMockMvc;

    private Allergy allergy;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Allergy createEntity() {
        Allergy allergy = new Allergy()
            .patientId(DEFAULT_PATIENT_ID)
            .name(DEFAULT_NAME)
            .category(DEFAULT_CATEGORY)
            .severity(DEFAULT_SEVERITY)
            .reaction(DEFAULT_REACTION)
            .notedOn(DEFAULT_NOTED_ON)
            .notedById(DEFAULT_NOTED_BY_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return allergy;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Allergy createUpdatedEntity() {
        Allergy allergy = new Allergy()
            .patientId(UPDATED_PATIENT_ID)
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .severity(UPDATED_SEVERITY)
            .reaction(UPDATED_REACTION)
            .notedOn(UPDATED_NOTED_ON)
            .notedById(UPDATED_NOTED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return allergy;
    }

    @BeforeEach
    public void initTest() {
        allergyRepository.deleteAll();
        allergy = createEntity();
    }

    @Test
    void createAllergy() throws Exception {
        int databaseSizeBeforeCreate = allergyRepository.findAll().size();
        // Create the Allergy
        restAllergyMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(allergy)))
            .andExpect(status().isCreated());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeCreate + 1);
        Allergy testAllergy = allergyList.get(allergyList.size() - 1);
        assertThat(testAllergy.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testAllergy.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testAllergy.getCategory()).isEqualTo(DEFAULT_CATEGORY);
        assertThat(testAllergy.getSeverity()).isEqualTo(DEFAULT_SEVERITY);
        assertThat(testAllergy.getReaction()).isEqualTo(DEFAULT_REACTION);
        assertThat(testAllergy.getNotedOn()).isEqualTo(DEFAULT_NOTED_ON);
        assertThat(testAllergy.getNotedById()).isEqualTo(DEFAULT_NOTED_BY_ID);
        assertThat(testAllergy.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testAllergy.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testAllergy.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testAllergy.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createAllergyWithExistingId() throws Exception {
        // Create the Allergy with an existing ID
        allergy.setId("existing_id");

        int databaseSizeBeforeCreate = allergyRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAllergyMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(allergy)))
            .andExpect(status().isBadRequest());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllAllergies() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        // Get all the allergyList
        restAllergyMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(allergy.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY.toString())))
            .andExpect(jsonPath("$.[*].severity").value(hasItem(DEFAULT_SEVERITY.toString())))
            .andExpect(jsonPath("$.[*].reaction").value(hasItem(DEFAULT_REACTION)))
            .andExpect(jsonPath("$.[*].notedOn").value(hasItem(DEFAULT_NOTED_ON.toString())))
            .andExpect(jsonPath("$.[*].notedById").value(hasItem(DEFAULT_NOTED_BY_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllAllergiesByPatientId() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        // The patient's own records come back
        restAllergyMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(allergy.getId())));

        // Another patient's id returns nothing rather than everything
        restAllergyMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllergy() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        // Get the allergy
        restAllergyMockMvc
            .perform(get(ENTITY_API_URL_ID, allergy.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(allergy.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY.toString()))
            .andExpect(jsonPath("$.severity").value(DEFAULT_SEVERITY.toString()))
            .andExpect(jsonPath("$.reaction").value(DEFAULT_REACTION))
            .andExpect(jsonPath("$.notedOn").value(DEFAULT_NOTED_ON.toString()))
            .andExpect(jsonPath("$.notedById").value(DEFAULT_NOTED_BY_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingAllergy() throws Exception {
        // Get the allergy
        restAllergyMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingAllergy() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();

        // Update the allergy
        Allergy updatedAllergy = allergyRepository.findById(allergy.getId()).orElseThrow();
        updatedAllergy
            .patientId(UPDATED_PATIENT_ID)
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .severity(UPDATED_SEVERITY)
            .reaction(UPDATED_REACTION)
            .notedOn(UPDATED_NOTED_ON)
            .notedById(UPDATED_NOTED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restAllergyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedAllergy.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedAllergy))
            )
            .andExpect(status().isOk());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
        Allergy testAllergy = allergyList.get(allergyList.size() - 1);
        assertThat(testAllergy.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testAllergy.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testAllergy.getCategory()).isEqualTo(UPDATED_CATEGORY);
        assertThat(testAllergy.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testAllergy.getReaction()).isEqualTo(UPDATED_REACTION);
        assertThat(testAllergy.getNotedOn()).isEqualTo(UPDATED_NOTED_ON);
        assertThat(testAllergy.getNotedById()).isEqualTo(UPDATED_NOTED_BY_ID);
        assertThat(testAllergy.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testAllergy.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testAllergy.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testAllergy.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, allergy.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(allergy))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(allergy))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(allergy)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateAllergyWithPatch() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();

        // Update the allergy using partial update
        Allergy partialUpdatedAllergy = new Allergy();
        partialUpdatedAllergy.setId(allergy.getId());

        partialUpdatedAllergy
            .severity(UPDATED_SEVERITY)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restAllergyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAllergy.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedAllergy))
            )
            .andExpect(status().isOk());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
        Allergy testAllergy = allergyList.get(allergyList.size() - 1);
        assertThat(testAllergy.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testAllergy.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testAllergy.getCategory()).isEqualTo(DEFAULT_CATEGORY);
        assertThat(testAllergy.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testAllergy.getReaction()).isEqualTo(DEFAULT_REACTION);
        assertThat(testAllergy.getNotedOn()).isEqualTo(DEFAULT_NOTED_ON);
        assertThat(testAllergy.getNotedById()).isEqualTo(DEFAULT_NOTED_BY_ID);
        assertThat(testAllergy.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testAllergy.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testAllergy.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testAllergy.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateAllergyWithPatch() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();

        // Update the allergy using partial update
        Allergy partialUpdatedAllergy = new Allergy();
        partialUpdatedAllergy.setId(allergy.getId());

        partialUpdatedAllergy
            .patientId(UPDATED_PATIENT_ID)
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .severity(UPDATED_SEVERITY)
            .reaction(UPDATED_REACTION)
            .notedOn(UPDATED_NOTED_ON)
            .notedById(UPDATED_NOTED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restAllergyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAllergy.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedAllergy))
            )
            .andExpect(status().isOk());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
        Allergy testAllergy = allergyList.get(allergyList.size() - 1);
        assertThat(testAllergy.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testAllergy.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testAllergy.getCategory()).isEqualTo(UPDATED_CATEGORY);
        assertThat(testAllergy.getSeverity()).isEqualTo(UPDATED_SEVERITY);
        assertThat(testAllergy.getReaction()).isEqualTo(UPDATED_REACTION);
        assertThat(testAllergy.getNotedOn()).isEqualTo(UPDATED_NOTED_ON);
        assertThat(testAllergy.getNotedById()).isEqualTo(UPDATED_NOTED_BY_ID);
        assertThat(testAllergy.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testAllergy.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testAllergy.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testAllergy.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, allergy.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(allergy))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(allergy))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamAllergy() throws Exception {
        int databaseSizeBeforeUpdate = allergyRepository.findAll().size();
        allergy.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllergyMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(allergy)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Allergy in the database
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteAllergy() throws Exception {
        // Initialize the database
        allergyRepository.save(allergy);

        int databaseSizeBeforeDelete = allergyRepository.findAll().size();

        // Delete the allergy
        restAllergyMockMvc
            .perform(delete(ENTITY_API_URL_ID, allergy.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Allergy> allergyList = allergyRepository.findAll();
        assertThat(allergyList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
