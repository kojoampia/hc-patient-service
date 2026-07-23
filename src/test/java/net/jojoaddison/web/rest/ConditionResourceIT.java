package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ConditionAsserts.*;
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
import net.jojoaddison.domain.Condition;
import net.jojoaddison.repository.ConditionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ConditionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ConditionResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/conditions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ConditionRepository conditionRepository;

    @Autowired
    private MockMvc restConditionMockMvc;

    private Condition condition;

    private Condition insertedCondition;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Condition createEntity() {
        return new Condition()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .patientId(DEFAULT_PATIENT_ID)
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
    public static Condition createUpdatedEntity() {
        return new Condition()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    void initTest() {
        condition = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedCondition != null) {
            conditionRepository.delete(insertedCondition);
            insertedCondition = null;
        }
    }

    @Test
    void createCondition() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Condition
        var returnedCondition = om.readValue(
            restConditionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(condition)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Condition.class
        );

        // Validate the Condition in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertConditionUpdatableFieldsEquals(returnedCondition, getPersistedCondition(returnedCondition));

        insertedCondition = returnedCondition;
    }

    @Test
    void createConditionWithExistingId() throws Exception {
        // Create the Condition with an existing ID
        condition.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restConditionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(condition)))
            .andExpect(status().isBadRequest());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllConditions() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        // Get all the conditionList
        restConditionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(condition.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getCondition() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        // Get the condition
        restConditionMockMvc
            .perform(get(ENTITY_API_URL_ID, condition.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(condition.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingCondition() throws Exception {
        // Get the condition
        restConditionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingCondition() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the condition
        Condition updatedCondition = conditionRepository.findById(condition.getId()).orElseThrow();
        updatedCondition
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedCondition.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedCondition))
            )
            .andExpect(status().isOk());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedConditionToMatchAllProperties(updatedCondition);
    }

    @Test
    void putNonExistingCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, condition.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(condition))
            )
            .andExpect(status().isBadRequest());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(condition))
            )
            .andExpect(status().isBadRequest());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(condition)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateConditionWithPatch() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the condition using partial update
        Condition partialUpdatedCondition = new Condition();
        partialUpdatedCondition.setId(condition.getId());

        partialUpdatedCondition
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCondition.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCondition))
            )
            .andExpect(status().isOk());

        // Validate the Condition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertConditionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedCondition, condition),
            getPersistedCondition(condition)
        );
    }

    @Test
    void fullUpdateConditionWithPatch() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the condition using partial update
        Condition partialUpdatedCondition = new Condition();
        partialUpdatedCondition.setId(condition.getId());

        partialUpdatedCondition
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCondition.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCondition))
            )
            .andExpect(status().isOk());

        // Validate the Condition in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertConditionUpdatableFieldsEquals(partialUpdatedCondition, getPersistedCondition(partialUpdatedCondition));
    }

    @Test
    void patchNonExistingCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, condition.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(condition))
            )
            .andExpect(status().isBadRequest());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(condition))
            )
            .andExpect(status().isBadRequest());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamCondition() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        condition.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConditionMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(condition)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Condition in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteCondition() throws Exception {
        // Initialize the database
        insertedCondition = conditionRepository.save(condition);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the condition
        restConditionMockMvc
            .perform(delete(ENTITY_API_URL_ID, condition.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return conditionRepository.count();
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

    protected Condition getPersistedCondition(Condition condition) {
        return conditionRepository.findById(condition.getId()).orElseThrow();
    }

    protected void assertPersistedConditionToMatchAllProperties(Condition expectedCondition) {
        assertConditionAllPropertiesEquals(expectedCondition, getPersistedCondition(expectedCondition));
    }

    protected void assertPersistedConditionToMatchUpdatableProperties(Condition expectedCondition) {
        assertConditionAllUpdatablePropertiesEquals(expectedCondition, getPersistedCondition(expectedCondition));
    }
}
