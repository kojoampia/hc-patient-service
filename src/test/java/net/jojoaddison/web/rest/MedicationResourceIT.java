package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.MedicationAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.repository.MedicationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link MedicationResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class MedicationResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PRESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_PRESCRIPTION = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/medications";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private MockMvc restMedicationMockMvc;

    private Medication medication;

    private Medication insertedMedication;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Medication createEntity() {
        return new Medication()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .patientId(DEFAULT_PATIENT_ID)
            .prescription(DEFAULT_PRESCRIPTION)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Medication createUpdatedEntity() {
        return new Medication()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    void initTest() {
        medication = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedMedication != null) {
            medicationRepository.delete(insertedMedication);
            insertedMedication = null;
        }
    }

    @Test
    void createMedication() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Medication
        var returnedMedication = om.readValue(
            restMedicationMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medication)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Medication.class
        );

        // Validate the Medication in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertMedicationUpdatableFieldsEquals(returnedMedication, getPersistedMedication(returnedMedication));

        insertedMedication = returnedMedication;
    }

    @Test
    void createMedicationWithExistingId() throws Exception {
        // Create the Medication with an existing ID
        medication.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMedicationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medication)))
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMedications() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        // Get all the medicationList
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(medication.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].prescription").value(hasItem(DEFAULT_PRESCRIPTION)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getMedication() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        // Get the medication
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL_ID, medication.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(medication.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.prescription").value(DEFAULT_PRESCRIPTION))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingMedication() throws Exception {
        // Get the medication
        restMedicationMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingMedication() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medication
        Medication updatedMedication = medicationRepository.findById(medication.getId()).orElseThrow();
        updatedMedication
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedMedication.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedMedicationToMatchAllProperties(updatedMedication);
    }

    @Test
    void putNonExistingMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, medication.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(medication)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMedicationWithPatch() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medication using partial update
        Medication partialUpdatedMedication = new Medication();
        partialUpdatedMedication.setId(medication.getId());

        partialUpdatedMedication
            .description(UPDATED_DESCRIPTION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedication.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMedicationUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedMedication, medication),
            getPersistedMedication(medication)
        );
    }

    @Test
    void fullUpdateMedicationWithPatch() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the medication using partial update
        Medication partialUpdatedMedication = new Medication();
        partialUpdatedMedication.setId(medication.getId());

        partialUpdatedMedication
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedication.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMedicationUpdatableFieldsEquals(partialUpdatedMedication, getPersistedMedication(partialUpdatedMedication));
    }

    @Test
    void patchNonExistingMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, medication.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMedication() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(medication)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Medication in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMedication() throws Exception {
        // Initialize the database
        insertedMedication = medicationRepository.save(medication);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the medication
        restMedicationMockMvc
            .perform(delete(ENTITY_API_URL_ID, medication.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return medicationRepository.count();
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

    protected Medication getPersistedMedication(Medication medication) {
        return medicationRepository.findById(medication.getId()).orElseThrow();
    }

    protected void assertPersistedMedicationToMatchAllProperties(Medication expectedMedication) {
        assertMedicationAllPropertiesEquals(expectedMedication, getPersistedMedication(expectedMedication));
    }

    protected void assertPersistedMedicationToMatchUpdatableProperties(Medication expectedMedication) {
        assertMedicationAllUpdatablePropertiesEquals(expectedMedication, getPersistedMedication(expectedMedication));
    }
}
