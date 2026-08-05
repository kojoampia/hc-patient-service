package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.domain.enumeration.StatFlag;
import net.jojoaddison.repository.StatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link StatResource} REST controller.
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
class StatResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final Double DEFAULT_VALUE = 1D;
    private static final Double UPDATED_VALUE = 2D;

    private static final Double DEFAULT_SECONDARY_VALUE = 1D;
    private static final Double UPDATED_SECONDARY_VALUE = 2D;

    private static final String DEFAULT_UNIT = "AAAAAAAAAA";
    private static final String UPDATED_UNIT = "BBBBBBBBBB";

    private static final Double DEFAULT_REFERENCE_LOW = 1D;
    private static final Double UPDATED_REFERENCE_LOW = 2D;

    private static final Double DEFAULT_REFERENCE_HIGH = 1D;
    private static final Double UPDATED_REFERENCE_HIGH = 2D;

    private static final StatFlag DEFAULT_FLAG = StatFlag.OK;
    private static final StatFlag UPDATED_FLAG = StatFlag.WARN;

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final Instant DEFAULT_RECORDED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RECORDED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/stats";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

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
            .patientId(DEFAULT_PATIENT_ID)
            .type(DEFAULT_TYPE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .value(DEFAULT_VALUE)
            .secondaryValue(DEFAULT_SECONDARY_VALUE)
            .unit(DEFAULT_UNIT)
            .referenceLow(DEFAULT_REFERENCE_LOW)
            .referenceHigh(DEFAULT_REFERENCE_HIGH)
            .flag(DEFAULT_FLAG)
            .note(DEFAULT_NOTE)
            .recordedAt(DEFAULT_RECORDED_AT)
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
            .patientId(UPDATED_PATIENT_ID)
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .secondaryValue(UPDATED_SECONDARY_VALUE)
            .unit(UPDATED_UNIT)
            .referenceLow(UPDATED_REFERENCE_LOW)
            .referenceHigh(UPDATED_REFERENCE_HIGH)
            .flag(UPDATED_FLAG)
            .note(UPDATED_NOTE)
            .recordedAt(UPDATED_RECORDED_AT)
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
        assertThat(testStat.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testStat.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(testStat.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testStat.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(DEFAULT_VALUE);
        assertThat(testStat.getSecondaryValue()).isEqualTo(DEFAULT_SECONDARY_VALUE);
        assertThat(testStat.getUnit()).isEqualTo(DEFAULT_UNIT);
        assertThat(testStat.getReferenceLow()).isEqualTo(DEFAULT_REFERENCE_LOW);
        assertThat(testStat.getReferenceHigh()).isEqualTo(DEFAULT_REFERENCE_HIGH);
        assertThat(testStat.getFlag()).isEqualTo(DEFAULT_FLAG);
        assertThat(testStat.getNote()).isEqualTo(DEFAULT_NOTE);
        assertThat(testStat.getRecordedAt()).isEqualTo(DEFAULT_RECORDED_AT);
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
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE.doubleValue())))
            .andExpect(jsonPath("$.[*].secondaryValue").value(hasItem(DEFAULT_SECONDARY_VALUE.doubleValue())))
            .andExpect(jsonPath("$.[*].unit").value(hasItem(DEFAULT_UNIT)))
            .andExpect(jsonPath("$.[*].referenceLow").value(hasItem(DEFAULT_REFERENCE_LOW.doubleValue())))
            .andExpect(jsonPath("$.[*].referenceHigh").value(hasItem(DEFAULT_REFERENCE_HIGH.doubleValue())))
            .andExpect(jsonPath("$.[*].flag").value(hasItem(DEFAULT_FLAG.toString())))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)))
            .andExpect(jsonPath("$.[*].recordedAt").value(hasItem(DEFAULT_RECORDED_AT.toString())))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }

    @Test
    void getAllStatsByPatientId() throws Exception {
        // Initialize the database
        statRepository.save(stat);

        // The patient's own records come back
        restStatMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(stat.getId())));

        // Another patient's id returns nothing rather than everything
        restStatMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
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
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.value").value(DEFAULT_VALUE.doubleValue()))
            .andExpect(jsonPath("$.secondaryValue").value(DEFAULT_SECONDARY_VALUE.doubleValue()))
            .andExpect(jsonPath("$.unit").value(DEFAULT_UNIT))
            .andExpect(jsonPath("$.referenceLow").value(DEFAULT_REFERENCE_LOW.doubleValue()))
            .andExpect(jsonPath("$.referenceHigh").value(DEFAULT_REFERENCE_HIGH.doubleValue()))
            .andExpect(jsonPath("$.flag").value(DEFAULT_FLAG.toString()))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE))
            .andExpect(jsonPath("$.recordedAt").value(DEFAULT_RECORDED_AT.toString()))
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
            .patientId(UPDATED_PATIENT_ID)
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .secondaryValue(UPDATED_SECONDARY_VALUE)
            .unit(UPDATED_UNIT)
            .referenceLow(UPDATED_REFERENCE_LOW)
            .referenceHigh(UPDATED_REFERENCE_HIGH)
            .flag(UPDATED_FLAG)
            .note(UPDATED_NOTE)
            .recordedAt(UPDATED_RECORDED_AT)
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
        assertThat(testStat.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testStat.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(UPDATED_VALUE);
        assertThat(testStat.getSecondaryValue()).isEqualTo(UPDATED_SECONDARY_VALUE);
        assertThat(testStat.getUnit()).isEqualTo(UPDATED_UNIT);
        assertThat(testStat.getReferenceLow()).isEqualTo(UPDATED_REFERENCE_LOW);
        assertThat(testStat.getReferenceHigh()).isEqualTo(UPDATED_REFERENCE_HIGH);
        assertThat(testStat.getFlag()).isEqualTo(UPDATED_FLAG);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getRecordedAt()).isEqualTo(UPDATED_RECORDED_AT);
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

        partialUpdatedStat
            .patientId(UPDATED_PATIENT_ID)
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .unit(UPDATED_UNIT)
            .referenceHigh(UPDATED_REFERENCE_HIGH)
            .flag(UPDATED_FLAG)
            .note(UPDATED_NOTE)
            .recordedAt(UPDATED_RECORDED_AT);

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
        assertThat(testStat.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testStat.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(UPDATED_VALUE);
        assertThat(testStat.getSecondaryValue()).isEqualTo(DEFAULT_SECONDARY_VALUE);
        assertThat(testStat.getUnit()).isEqualTo(UPDATED_UNIT);
        assertThat(testStat.getReferenceLow()).isEqualTo(DEFAULT_REFERENCE_LOW);
        assertThat(testStat.getReferenceHigh()).isEqualTo(UPDATED_REFERENCE_HIGH);
        assertThat(testStat.getFlag()).isEqualTo(UPDATED_FLAG);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getRecordedAt()).isEqualTo(UPDATED_RECORDED_AT);
        assertThat(testStat.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
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
            .patientId(UPDATED_PATIENT_ID)
            .type(UPDATED_TYPE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .value(UPDATED_VALUE)
            .secondaryValue(UPDATED_SECONDARY_VALUE)
            .unit(UPDATED_UNIT)
            .referenceLow(UPDATED_REFERENCE_LOW)
            .referenceHigh(UPDATED_REFERENCE_HIGH)
            .flag(UPDATED_FLAG)
            .note(UPDATED_NOTE)
            .recordedAt(UPDATED_RECORDED_AT)
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
        assertThat(testStat.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testStat.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testStat.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testStat.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testStat.getValue()).isEqualTo(UPDATED_VALUE);
        assertThat(testStat.getSecondaryValue()).isEqualTo(UPDATED_SECONDARY_VALUE);
        assertThat(testStat.getUnit()).isEqualTo(UPDATED_UNIT);
        assertThat(testStat.getReferenceLow()).isEqualTo(UPDATED_REFERENCE_LOW);
        assertThat(testStat.getReferenceHigh()).isEqualTo(UPDATED_REFERENCE_HIGH);
        assertThat(testStat.getFlag()).isEqualTo(UPDATED_FLAG);
        assertThat(testStat.getNote()).isEqualTo(UPDATED_NOTE);
        assertThat(testStat.getRecordedAt()).isEqualTo(UPDATED_RECORDED_AT);
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
}
