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
import net.jojoaddison.domain.Stat;
import net.jojoaddison.repository.StatRepository;
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
    private static final String ENTITY_SEARCH_API_URL = "/api/stats/_search";

    @Autowired
    private StatRepository statRepository;

    @Autowired
    private MockMvc restStatMockMvc;

    private Stat stat;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Stat createEntity() {
        Stat stat = new Stat()
            .type(DEFAULT_TYPE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .value(DEFAULT_VALUE)
            .note(DEFAULT_NOTE)
            .patientId(DEFAULT_PATIENT_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY);
        return stat;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Stat createUpdatedEntity() {
        Stat stat = new Stat()
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .note(UPDATED_NOTE)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);
        return stat;
    }

    @BeforeEach
    public void initTest() {
        statRepository.deleteAll();
        stat = createEntity();
    }

    @Test
    void createStat() throws Exception {
        int databaseSizeBeforeCreate = statRepository.findAll().size();
        // Create the Stat
        restStatMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(stat)))
            .andExpect(status().isCreated());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeCreate + 1);
        Stat testStat = statList.get(statList.size() - 1);
        assertThat(testStat.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(testStat.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testStat.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(DEFAULT_VALUE);
        assertThat(testStat.getNote()).isEqualTo(DEFAULT_NOTE);
        assertThat(testStat.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testStat.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testStat.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
    }

    @Test
    void createStatWithExistingId() throws Exception {
        // Create the Stat with an existing ID
        stat.setId("existing_id");

        int databaseSizeBeforeCreate = statRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restStatMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(stat)))
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllStats() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        // Get all the statList
        restStatMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(stat.getId())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE.doubleValue())))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }

    @Test
    void getStat() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        // Get the stat
        restStatMockMvc
            .perform(get(ENTITY_API_URL_ID, stat.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(stat.getId()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.value").value(DEFAULT_VALUE.doubleValue()))
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
        statRepository.save(stat);

        int databaseSizeBeforeUpdate = statRepository.findAll().size();

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
                    .content(TestUtil.convertObjectToJsonBytes(updatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
        Stat testStat = statList.get(statList.size() - 1);
        assertThat(testStat.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(UPDATED_VALUE);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testStat.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testStat.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
    }

    @Test
    void putNonExistingStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                put(ENTITY_API_URL_ID, stat.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(stat)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateStatWithPatch() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        int databaseSizeBeforeUpdate = statRepository.findAll().size();

        // Update the stat using partial update
        Stat partialUpdatedStat = new Stat();
        partialUpdatedStat.setId(stat.getId());

        partialUpdatedStat.name(UPDATED_NAME).note(UPDATED_NOTE).createdDate(UPDATED_CREATED_DATE);

        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedStat.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
        Stat testStat = statList.get(statList.size() - 1);
        assertThat(testStat.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(DEFAULT_VALUE);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testStat.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testStat.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
    }

    @Test
    void fullUpdateStatWithPatch() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        int databaseSizeBeforeUpdate = statRepository.findAll().size();

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
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedStat))
            )
            .andExpect(status().isOk());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
        Stat testStat = statList.get(statList.size() - 1);
        assertThat(testStat.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(UPDATED_VALUE);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testStat.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testStat.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
    }

    @Test
    void patchNonExistingStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, stat.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(stat))
            )
            .andExpect(status().isBadRequest());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamStat() throws Exception {
        int databaseSizeBeforeUpdate = statRepository.findAll().size();
        stat.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restStatMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(stat)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Stat in the database
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteStat() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        int databaseSizeBeforeDelete = statRepository.findAll().size();

        // Delete the stat
        restStatMockMvc
            .perform(delete(ENTITY_API_URL_ID, stat.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Stat> statList = statRepository.findAll();
        assertThat(statList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    void searchStat() throws Exception {
        // Initialize the database
        stat = statRepository.save(stat);

        // Search the stat
        restStatMockMvc
            .perform(get(ENTITY_SEARCH_API_URL + "?query=id:" + stat.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(stat.getId())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE.doubleValue())))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }
}
