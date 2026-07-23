package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.StatAsserts.*;
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
import net.jojoaddison.domain.Stat;
import net.jojoaddison.repository.StatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link StatResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class StatResourceIT {

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final Double DEFAULT_VALUE = 1D;
    private static final Double UPDATED_VALUE = 2D;

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/stats";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private StatRepository statRepository;

    @Autowired
    private MockMvc restStatMockMvc;

    private Stat stat;

    private Stat insertedStat;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Stat createEntity() {
        return new Stat()
            .type(DEFAULT_TYPE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .value(DEFAULT_VALUE)
            .note(DEFAULT_NOTE)
            .patientId(DEFAULT_PATIENT_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Stat createUpdatedEntity() {
        return new Stat()
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .note(UPDATED_NOTE)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);
    }

    @BeforeEach
    void initTest() {
        stat = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedStat != null) {
            statRepository.delete(insertedStat);
            insertedStat = null;
        }
    }

    @Test
    void createStat() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Stat
        var returnedStat = om.readValue(
            restStatMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(stat)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Stat.class
        );

        // Validate the Stat in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertStatUpdatableFieldsEquals(returnedStat, getPersistedStat(returnedStat));

        insertedStat = returnedStat;
    }

    @Test
    void createStatWithExistingId() throws Exception {
        // Create the Stat with an existing ID
        stat.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restStatMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(stat)))
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllStats() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        // Get all the statList
        restStatMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(stat.getId())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }

    @Test
    void getStat() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        // Get the stat
        restStatMockMvc
            .perform(get(ENTITY_API_URL_ID, stat.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(stat.getId()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.value").value(DEFAULT_VALUE))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY));
    }

    @Test
    void getNonExistingStat() throws Exception {
        // Get the stat
        restStatMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingStat() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the stat
        Stat updatedStat = statRepository.findById(stat.getId()).orElseThrow();
        updatedStat
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .note(UPDATED_NOTE)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restStatMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedStat.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedStatToMatchAllProperties(updatedStat);
    }

    @Test
    void putNonExistingStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(put(ENTITY_API_URL_ID, stat.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(stat)))
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(stat)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateStatWithPatch() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the stat using partial update
        Stat partialUpdatedStat = new Stat();
        partialUpdatedStat.setId(stat.getId());

        partialUpdatedStat
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE);

        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedStat.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertStatUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedStat, stat), getPersistedStat(stat));
    }

    @Test
    void fullUpdateStatWithPatch() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the stat using partial update
        Stat partialUpdatedStat = new Stat();
        partialUpdatedStat.setId(stat.getId());

        partialUpdatedStat
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .note(UPDATED_NOTE)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedStat.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertStatUpdatableFieldsEquals(partialUpdatedStat, getPersistedStat(partialUpdatedStat));
    }

    @Test
    void patchNonExistingStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(patch(ENTITY_API_URL_ID, stat.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(stat)))
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamStat() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(stat)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Stat in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteStat() throws Exception {
        // Initialize the database
        insertedStat = statRepository.save(stat);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the stat
        restStatMockMvc
            .perform(delete(ENTITY_API_URL_ID, stat.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return statRepository.count();
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

    protected Stat getPersistedStat(Stat stat) {
        return statRepository.findById(stat.getId()).orElseThrow();
    }

    protected void assertPersistedStatToMatchAllProperties(Stat expectedStat) {
        assertStatAllPropertiesEquals(expectedStat, getPersistedStat(expectedStat));
    }

    protected void assertPersistedStatToMatchUpdatableProperties(Stat expectedStat) {
        assertStatAllUpdatablePropertiesEquals(expectedStat, getPersistedStat(expectedStat));
    }
}
