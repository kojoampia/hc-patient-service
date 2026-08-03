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
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.enumeration.MedicationStatus;
import net.jojoaddison.repository.MedicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    private static final String DEFAULT_CASE_ID = "AAAAAAAAAA";
    private static final String UPDATED_CASE_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PRESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_PRESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_DOSAGE = "AAAAAAAAAA";
    private static final String UPDATED_DOSAGE = "BBBBBBBBBB";

    private static final MedicationStatus DEFAULT_STATUS = MedicationStatus.ACTIVE;
    private static final MedicationStatus UPDATED_STATUS = MedicationStatus.COMPLETED;

    private static final LocalDate DEFAULT_STARTED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_STARTED_ON = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_PRESCRIBED_BY_ID = "AAAAAAAAAA";
    private static final String UPDATED_PRESCRIBED_BY_ID = "BBBBBBBBBB";

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
    private MedicationRepository medicationRepository;

    @Autowired
    private MockMvc restMedicationMockMvc;

    private Medication medication;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Medication createEntity() {
        Medication medication = new Medication()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .patientId(DEFAULT_PATIENT_ID)
            .caseId(DEFAULT_CASE_ID)
            .prescription(DEFAULT_PRESCRIPTION)
            .dosage(DEFAULT_DOSAGE)
            .status(DEFAULT_STATUS)
            .startedOn(DEFAULT_STARTED_ON)
            .prescribedById(DEFAULT_PRESCRIBED_BY_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return medication;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Medication createUpdatedEntity() {
        Medication medication = new Medication()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .dosage(UPDATED_DOSAGE)
            .status(UPDATED_STATUS)
            .startedOn(UPDATED_STARTED_ON)
            .prescribedById(UPDATED_PRESCRIBED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return medication;
    }

    @BeforeEach
    public void initTest() {
        medicationRepository.deleteAll();
        medication = createEntity();
    }

    @Test
    void createMedication() throws Exception {
        int databaseSizeBeforeCreate = medicationRepository.findAll().size();
        // Create the Medication
        restMedicationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(medication)))
            .andExpect(status().isCreated());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeCreate + 1);
        Medication testMedication = medicationList.get(medicationList.size() - 1);
        assertThat(testMedication.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testMedication.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testMedication.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testMedication.getCaseId()).isEqualTo(DEFAULT_CASE_ID);
        assertThat(testMedication.getPrescription()).isEqualTo(DEFAULT_PRESCRIPTION);
        assertThat(testMedication.getDosage()).isEqualTo(DEFAULT_DOSAGE);
        assertThat(testMedication.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testMedication.getStartedOn()).isEqualTo(DEFAULT_STARTED_ON);
        assertThat(testMedication.getPrescribedById()).isEqualTo(DEFAULT_PRESCRIBED_BY_ID);
        assertThat(testMedication.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testMedication.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testMedication.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testMedication.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createMedicationWithExistingId() throws Exception {
        // Create the Medication with an existing ID
        medication.setId("existing_id");

        int databaseSizeBeforeCreate = medicationRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMedicationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(medication)))
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMedications() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        // Get all the medicationList
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(medication.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].caseId").value(hasItem(DEFAULT_CASE_ID)))
            .andExpect(jsonPath("$.[*].prescription").value(hasItem(DEFAULT_PRESCRIPTION)))
            .andExpect(jsonPath("$.[*].dosage").value(hasItem(DEFAULT_DOSAGE)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].startedOn").value(hasItem(DEFAULT_STARTED_ON.toString())))
            .andExpect(jsonPath("$.[*].prescribedById").value(hasItem(DEFAULT_PRESCRIBED_BY_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllMedicationsByPatientId() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        // The patient's own records come back
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(medication.getId())));

        // Another patient's id returns nothing rather than everything
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMedication() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        // Get the medication
        restMedicationMockMvc
            .perform(get(ENTITY_API_URL_ID, medication.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(medication.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.caseId").value(DEFAULT_CASE_ID))
            .andExpect(jsonPath("$.prescription").value(DEFAULT_PRESCRIPTION))
            .andExpect(jsonPath("$.dosage").value(DEFAULT_DOSAGE))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.startedOn").value(DEFAULT_STARTED_ON.toString()))
            .andExpect(jsonPath("$.prescribedById").value(DEFAULT_PRESCRIBED_BY_ID))
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
        medicationRepository.save(medication);

        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();

        // Update the medication
        Medication updatedMedication = medicationRepository.findById(medication.getId()).orElseThrow();
        updatedMedication
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .dosage(UPDATED_DOSAGE)
            .status(UPDATED_STATUS)
            .startedOn(UPDATED_STARTED_ON)
            .prescribedById(UPDATED_PRESCRIBED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedMedication.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
        Medication testMedication = medicationList.get(medicationList.size() - 1);
        assertThat(testMedication.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMedication.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testMedication.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testMedication.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testMedication.getPrescription()).isEqualTo(UPDATED_PRESCRIPTION);
        assertThat(testMedication.getDosage()).isEqualTo(UPDATED_DOSAGE);
        assertThat(testMedication.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testMedication.getStartedOn()).isEqualTo(UPDATED_STARTED_ON);
        assertThat(testMedication.getPrescribedById()).isEqualTo(UPDATED_PRESCRIBED_BY_ID);
        assertThat(testMedication.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testMedication.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testMedication.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testMedication.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, medication.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(medication)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMedicationWithPatch() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();

        // Update the medication using partial update
        Medication partialUpdatedMedication = new Medication();
        partialUpdatedMedication.setId(medication.getId());

        partialUpdatedMedication
            .name(UPDATED_NAME)
            .caseId(UPDATED_CASE_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedication.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
        Medication testMedication = medicationList.get(medicationList.size() - 1);
        assertThat(testMedication.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMedication.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testMedication.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testMedication.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testMedication.getPrescription()).isEqualTo(UPDATED_PRESCRIPTION);
        assertThat(testMedication.getDosage()).isEqualTo(DEFAULT_DOSAGE);
        assertThat(testMedication.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testMedication.getStartedOn()).isEqualTo(DEFAULT_STARTED_ON);
        assertThat(testMedication.getPrescribedById()).isEqualTo(DEFAULT_PRESCRIBED_BY_ID);
        assertThat(testMedication.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testMedication.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testMedication.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testMedication.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateMedicationWithPatch() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();

        // Update the medication using partial update
        Medication partialUpdatedMedication = new Medication();
        partialUpdatedMedication.setId(medication.getId());

        partialUpdatedMedication
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .prescription(UPDATED_PRESCRIPTION)
            .dosage(UPDATED_DOSAGE)
            .status(UPDATED_STATUS)
            .startedOn(UPDATED_STARTED_ON)
            .prescribedById(UPDATED_PRESCRIBED_BY_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMedication.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedMedication))
            )
            .andExpect(status().isOk());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
        Medication testMedication = medicationList.get(medicationList.size() - 1);
        assertThat(testMedication.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMedication.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testMedication.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testMedication.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testMedication.getPrescription()).isEqualTo(UPDATED_PRESCRIPTION);
        assertThat(testMedication.getDosage()).isEqualTo(UPDATED_DOSAGE);
        assertThat(testMedication.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testMedication.getStartedOn()).isEqualTo(UPDATED_STARTED_ON);
        assertThat(testMedication.getPrescribedById()).isEqualTo(UPDATED_PRESCRIBED_BY_ID);
        assertThat(testMedication.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testMedication.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testMedication.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testMedication.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, medication.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(medication))
            )
            .andExpect(status().isBadRequest());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMedication() throws Exception {
        int databaseSizeBeforeUpdate = medicationRepository.findAll().size();
        medication.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMedicationMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(medication))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Medication in the database
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMedication() throws Exception {
        // Initialize the database
        medicationRepository.save(medication);

        int databaseSizeBeforeDelete = medicationRepository.findAll().size();

        // Delete the medication
        restMedicationMockMvc
            .perform(delete(ENTITY_API_URL_ID, medication.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Medication> medicationList = medicationRepository.findAll();
        assertThat(medicationList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
