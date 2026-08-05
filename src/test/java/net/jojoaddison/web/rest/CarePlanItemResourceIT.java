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
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.domain.enumeration.CarePlanType;
import net.jojoaddison.repository.CarePlanItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link CarePlanItemResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
/*
 * Runs as ROLE_ADMIN, which {@link net.jojoaddison.security.PatientScope} treats as unrestricted.
 *
 * <p>That is deliberate and it is a narrowing of what this class covers: these tests exercise the CRUD
 * mechanics — status codes, id validation, partial update semantics — and say nothing about who may see
 * what. A default {@code @WithMockUser} is a ROLE_USER with no JWT and therefore no email claim, which now
 * correctly resolves to "no patient" and would make every assertion here fail for reasons unrelated to the
 * behaviour under test.</p>
 *
 * <p>The authorization rules themselves are covered by {@code PatientScopeIT}, which is where a
 * cross-patient regression will be caught. Do not "fix" a failure here by widening PatientScope.</p>
 */
@WithMockUser(authorities = { "ROLE_ADMIN" })
class CarePlanItemResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final CarePlanType DEFAULT_PLAN_TYPE = CarePlanType.DIET;
    private static final CarePlanType UPDATED_PLAN_TYPE = CarePlanType.EXERCISE;

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final String DEFAULT_DETAIL = "AAAAAAAAAA";
    private static final String UPDATED_DETAIL = "BBBBBBBBBB";

    private static final String DEFAULT_CADENCE = "AAAAAAAAAA";
    private static final String UPDATED_CADENCE = "BBBBBBBBBB";

    private static final Boolean DEFAULT_COMPLETED = false;
    private static final Boolean UPDATED_COMPLETED = true;

    private static final Integer DEFAULT_SORT_ORDER = 1;
    private static final Integer UPDATED_SORT_ORDER = 2;

    // Audit fields are stamped by the server from the token (AuditStamp), not taken from the request body, so the
    // expected value is the same whatever the test sends. "user" is the login @WithMockUser gives the caller. That
    // these constants no longer vary is the point: an audit field a client can choose is not an audit field.
    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "user";
    private static final String UPDATED_CREATED_BY = "user";

    private static final String DEFAULT_MODIFIED_BY = "user";
    private static final String UPDATED_MODIFIED_BY = "user";

    private static final String ENTITY_API_URL = "/api/care-plan-items";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private CarePlanItemRepository carePlanItemRepository;

    @Autowired
    private MockMvc restCarePlanItemMockMvc;

    private CarePlanItem carePlanItem;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CarePlanItem createEntity() {
        CarePlanItem carePlanItem = new CarePlanItem()
            .patientId(DEFAULT_PATIENT_ID)
            .planType(DEFAULT_PLAN_TYPE)
            .label(DEFAULT_LABEL)
            .detail(DEFAULT_DETAIL)
            .cadence(DEFAULT_CADENCE)
            .completed(DEFAULT_COMPLETED)
            .sortOrder(DEFAULT_SORT_ORDER)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return carePlanItem;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CarePlanItem createUpdatedEntity() {
        CarePlanItem carePlanItem = new CarePlanItem()
            .patientId(UPDATED_PATIENT_ID)
            .planType(UPDATED_PLAN_TYPE)
            .label(UPDATED_LABEL)
            .detail(UPDATED_DETAIL)
            .cadence(UPDATED_CADENCE)
            .completed(UPDATED_COMPLETED)
            .sortOrder(UPDATED_SORT_ORDER)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return carePlanItem;
    }

    @BeforeEach
    public void initTest() {
        carePlanItemRepository.deleteAll();
        carePlanItem = createEntity();
    }

    @Test
    void createCarePlanItem() throws Exception {
        int databaseSizeBeforeCreate = carePlanItemRepository.findAll().size();
        // Create the CarePlanItem
        restCarePlanItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(carePlanItem)))
            .andExpect(status().isCreated());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeCreate + 1);
        CarePlanItem testCarePlanItem = carePlanItemList.get(carePlanItemList.size() - 1);
        assertThat(testCarePlanItem.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testCarePlanItem.getPlanType()).isEqualTo(DEFAULT_PLAN_TYPE);
        assertThat(testCarePlanItem.getLabel()).isEqualTo(DEFAULT_LABEL);
        assertThat(testCarePlanItem.getDetail()).isEqualTo(DEFAULT_DETAIL);
        assertThat(testCarePlanItem.getCadence()).isEqualTo(DEFAULT_CADENCE);
        assertThat(testCarePlanItem.getCompleted()).isEqualTo(DEFAULT_COMPLETED);
        assertThat(testCarePlanItem.getSortOrder()).isEqualTo(DEFAULT_SORT_ORDER);
        assertThat(testCarePlanItem.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testCarePlanItem.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testCarePlanItem.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testCarePlanItem.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createCarePlanItemWithExistingId() throws Exception {
        // Create the CarePlanItem with an existing ID
        carePlanItem.setId("existing_id");

        int databaseSizeBeforeCreate = carePlanItemRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restCarePlanItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(carePlanItem)))
            .andExpect(status().isBadRequest());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllCarePlanItems() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        // Get all the carePlanItemList
        restCarePlanItemMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(carePlanItem.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].planType").value(hasItem(DEFAULT_PLAN_TYPE.toString())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].detail").value(hasItem(DEFAULT_DETAIL)))
            .andExpect(jsonPath("$.[*].cadence").value(hasItem(DEFAULT_CADENCE)))
            .andExpect(jsonPath("$.[*].completed").value(hasItem(DEFAULT_COMPLETED.booleanValue())))
            .andExpect(jsonPath("$.[*].sortOrder").value(hasItem(DEFAULT_SORT_ORDER)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllCarePlanItemsByPatientId() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        // The patient's own records come back
        restCarePlanItemMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(carePlanItem.getId())));

        // Another patient's id returns nothing rather than everything
        restCarePlanItemMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getCarePlanItem() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        // Get the carePlanItem
        restCarePlanItemMockMvc
            .perform(get(ENTITY_API_URL_ID, carePlanItem.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(carePlanItem.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.planType").value(DEFAULT_PLAN_TYPE.toString()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.detail").value(DEFAULT_DETAIL))
            .andExpect(jsonPath("$.cadence").value(DEFAULT_CADENCE))
            .andExpect(jsonPath("$.completed").value(DEFAULT_COMPLETED.booleanValue()))
            .andExpect(jsonPath("$.sortOrder").value(DEFAULT_SORT_ORDER))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingCarePlanItem() throws Exception {
        // Get the carePlanItem
        restCarePlanItemMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingCarePlanItem() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();

        // Update the carePlanItem
        CarePlanItem updatedCarePlanItem = carePlanItemRepository.findById(carePlanItem.getId()).orElseThrow();
        updatedCarePlanItem
            .patientId(UPDATED_PATIENT_ID)
            .planType(UPDATED_PLAN_TYPE)
            .label(UPDATED_LABEL)
            .detail(UPDATED_DETAIL)
            .cadence(UPDATED_CADENCE)
            .completed(UPDATED_COMPLETED)
            .sortOrder(UPDATED_SORT_ORDER)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restCarePlanItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedCarePlanItem.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedCarePlanItem))
            )
            .andExpect(status().isOk());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
        CarePlanItem testCarePlanItem = carePlanItemList.get(carePlanItemList.size() - 1);
        assertThat(testCarePlanItem.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testCarePlanItem.getPlanType()).isEqualTo(UPDATED_PLAN_TYPE);
        assertThat(testCarePlanItem.getLabel()).isEqualTo(UPDATED_LABEL);
        assertThat(testCarePlanItem.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testCarePlanItem.getCadence()).isEqualTo(UPDATED_CADENCE);
        assertThat(testCarePlanItem.getCompleted()).isEqualTo(UPDATED_COMPLETED);
        assertThat(testCarePlanItem.getSortOrder()).isEqualTo(UPDATED_SORT_ORDER);
        assertThat(testCarePlanItem.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testCarePlanItem.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testCarePlanItem.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testCarePlanItem.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, carePlanItem.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(carePlanItem))
            )
            .andExpect(status().isBadRequest());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(carePlanItem))
            )
            .andExpect(status().isBadRequest());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(carePlanItem)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateCarePlanItemWithPatch() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();

        // Update the carePlanItem using partial update
        CarePlanItem partialUpdatedCarePlanItem = new CarePlanItem();
        partialUpdatedCarePlanItem.setId(carePlanItem.getId());

        partialUpdatedCarePlanItem
            .patientId(UPDATED_PATIENT_ID)
            .label(UPDATED_LABEL)
            .cadence(UPDATED_CADENCE)
            .completed(UPDATED_COMPLETED)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restCarePlanItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCarePlanItem.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedCarePlanItem))
            )
            .andExpect(status().isOk());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
        CarePlanItem testCarePlanItem = carePlanItemList.get(carePlanItemList.size() - 1);
        assertThat(testCarePlanItem.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testCarePlanItem.getPlanType()).isEqualTo(DEFAULT_PLAN_TYPE);
        assertThat(testCarePlanItem.getLabel()).isEqualTo(UPDATED_LABEL);
        assertThat(testCarePlanItem.getDetail()).isEqualTo(DEFAULT_DETAIL);
        assertThat(testCarePlanItem.getCadence()).isEqualTo(UPDATED_CADENCE);
        assertThat(testCarePlanItem.getCompleted()).isEqualTo(UPDATED_COMPLETED);
        assertThat(testCarePlanItem.getSortOrder()).isEqualTo(DEFAULT_SORT_ORDER);
        assertThat(testCarePlanItem.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testCarePlanItem.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testCarePlanItem.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testCarePlanItem.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void fullUpdateCarePlanItemWithPatch() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();

        // Update the carePlanItem using partial update
        CarePlanItem partialUpdatedCarePlanItem = new CarePlanItem();
        partialUpdatedCarePlanItem.setId(carePlanItem.getId());

        partialUpdatedCarePlanItem
            .patientId(UPDATED_PATIENT_ID)
            .planType(UPDATED_PLAN_TYPE)
            .label(UPDATED_LABEL)
            .detail(UPDATED_DETAIL)
            .cadence(UPDATED_CADENCE)
            .completed(UPDATED_COMPLETED)
            .sortOrder(UPDATED_SORT_ORDER)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restCarePlanItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCarePlanItem.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedCarePlanItem))
            )
            .andExpect(status().isOk());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
        CarePlanItem testCarePlanItem = carePlanItemList.get(carePlanItemList.size() - 1);
        assertThat(testCarePlanItem.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testCarePlanItem.getPlanType()).isEqualTo(UPDATED_PLAN_TYPE);
        assertThat(testCarePlanItem.getLabel()).isEqualTo(UPDATED_LABEL);
        assertThat(testCarePlanItem.getDetail()).isEqualTo(UPDATED_DETAIL);
        assertThat(testCarePlanItem.getCadence()).isEqualTo(UPDATED_CADENCE);
        assertThat(testCarePlanItem.getCompleted()).isEqualTo(UPDATED_COMPLETED);
        assertThat(testCarePlanItem.getSortOrder()).isEqualTo(UPDATED_SORT_ORDER);
        assertThat(testCarePlanItem.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testCarePlanItem.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testCarePlanItem.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testCarePlanItem.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, carePlanItem.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(carePlanItem))
            )
            .andExpect(status().isBadRequest());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(carePlanItem))
            )
            .andExpect(status().isBadRequest());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamCarePlanItem() throws Exception {
        int databaseSizeBeforeUpdate = carePlanItemRepository.findAll().size();
        carePlanItem.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCarePlanItemMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(carePlanItem))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the CarePlanItem in the database
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteCarePlanItem() throws Exception {
        // Initialize the database
        carePlanItemRepository.save(carePlanItem);

        int databaseSizeBeforeDelete = carePlanItemRepository.findAll().size();

        // Delete the carePlanItem
        restCarePlanItemMockMvc
            .perform(delete(ENTITY_API_URL_ID, carePlanItem.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<CarePlanItem> carePlanItemList = carePlanItemRepository.findAll();
        assertThat(carePlanItemList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
