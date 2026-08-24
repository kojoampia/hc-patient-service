package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Shift;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ShiftResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
/*
 * Runs as ROLE_ADMIN. This entity is reference data: any authenticated caller may read it, but creating, updating
 * and deleting requires ROLE_ADMIN or a clinical discipline. A default @WithMockUser is a ROLE_USER and would get 403
 * on every write here, which says nothing about the CRUD mechanics these tests exist to cover.
 *
 * The rule itself — that a patient can read but not write — is covered by ReferenceDataIT.
 */
@WithMockUser(authorities = { "ROLE_ADMIN" })
class ShiftResourceIT {

    private static final String DEFAULT_ROSTER_ID = "AAAAAAAAAA";
    private static final String UPDATED_ROSTER_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_ID = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_STARTS_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_STARTS_AT = Instant.ofEpochMilli(3600000L);

    private static final Instant DEFAULT_ENDS_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_ENDS_AT = Instant.ofEpochMilli(3600000L);

    private static final ShiftStatus DEFAULT_STATUS = ShiftStatus.ACTIVE;
    private static final ShiftStatus UPDATED_STATUS = ShiftStatus.UPCOMING;

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now();

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now();

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/shifts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private MockMvc restShiftMockMvc;

    private Shift shift;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Shift createEntity() {
        return new Shift()
            .rosterId(DEFAULT_ROSTER_ID)
            .professionalId(DEFAULT_PROFESSIONAL_ID)
            .startsAt(DEFAULT_STARTS_AT)
            .endsAt(DEFAULT_ENDS_AT)
            .status(DEFAULT_STATUS)
            .notes(DEFAULT_NOTES)
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
    public static Shift createUpdatedEntity() {
        return new Shift()
            .rosterId(UPDATED_ROSTER_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .startsAt(UPDATED_STARTS_AT)
            .endsAt(UPDATED_ENDS_AT)
            .status(UPDATED_STATUS)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    public void initTest() {
        shiftRepository.deleteAll();
        shift = createEntity();
    }

    @Test
    void createShift() throws Exception {
        int databaseSizeBeforeCreate = shiftRepository.findAll().size();

        restShiftMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(shift)))
            .andExpect(status().isCreated());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeCreate + 1);
        Shift testShift = shiftList.get(shiftList.size() - 1);
        assertThat(testShift.getRosterId()).isEqualTo(DEFAULT_ROSTER_ID);
        assertThat(testShift.getProfessionalId()).isEqualTo(DEFAULT_PROFESSIONAL_ID);
        assertThat(testShift.getStartsAt()).isEqualTo(DEFAULT_STARTS_AT);
        assertThat(testShift.getEndsAt()).isEqualTo(DEFAULT_ENDS_AT);
        assertThat(testShift.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testShift.getNotes()).isEqualTo(DEFAULT_NOTES);
        assertThat(testShift.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testShift.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createShiftWithExistingId() throws Exception {
        shift.setId("existing_id");

        int databaseSizeBeforeCreate = shiftRepository.findAll().size();

        restShiftMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(shift)))
            .andExpect(status().isBadRequest());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllShifts() throws Exception {
        shiftRepository.save(shift);

        restShiftMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(shift.getId())))
            .andExpect(jsonPath("$.[*].rosterId").value(hasItem(DEFAULT_ROSTER_ID)))
            .andExpect(jsonPath("$.[*].professionalId").value(hasItem(DEFAULT_PROFESSIONAL_ID)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)));
    }

    @Test
    void getAllShiftsFilteredByRosterAndProfessional() throws Exception {
        shiftRepository.save(shift);
        Shift otherShift = shiftRepository.save(createUpdatedEntity());

        // Both filters exist because a roster screen asks for one roster's shifts and a clinician's own screen asks
        // for theirs; together they narrow to one person's turn on one roster.
        restShiftMockMvc
            .perform(get(ENTITY_API_URL + "?rosterId={id}", DEFAULT_ROSTER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(shift.getId())))
            .andExpect(jsonPath("$.length()").value(1));

        restShiftMockMvc
            .perform(get(ENTITY_API_URL + "?professionalId={id}", UPDATED_PROFESSIONAL_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherShift.getId())))
            .andExpect(jsonPath("$.length()").value(1));

        restShiftMockMvc
            .perform(get(ENTITY_API_URL + "?rosterId={roster}&professionalId={professional}", DEFAULT_ROSTER_ID, DEFAULT_PROFESSIONAL_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        restShiftMockMvc
            .perform(get(ENTITY_API_URL + "?rosterId={roster}&professionalId={professional}", DEFAULT_ROSTER_ID, UPDATED_PROFESSIONAL_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getShift() throws Exception {
        shiftRepository.save(shift);

        restShiftMockMvc
            .perform(get(ENTITY_API_URL_ID, shift.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(shift.getId()))
            .andExpect(jsonPath("$.rosterId").value(DEFAULT_ROSTER_ID))
            .andExpect(jsonPath("$.professionalId").value(DEFAULT_PROFESSIONAL_ID))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES));
    }

    @Test
    void getNonExistingShift() throws Exception {
        restShiftMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingShift() throws Exception {
        shiftRepository.save(shift);

        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();

        Shift updatedShift = shiftRepository.findById(shift.getId()).orElseThrow();
        updatedShift
            .rosterId(UPDATED_ROSTER_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .startsAt(UPDATED_STARTS_AT)
            .endsAt(UPDATED_ENDS_AT)
            .status(UPDATED_STATUS)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restShiftMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedShift.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedShift))
            )
            .andExpect(status().isOk());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
        Shift testShift = shiftList.get(shiftList.size() - 1);
        assertThat(testShift.getRosterId()).isEqualTo(UPDATED_ROSTER_ID);
        assertThat(testShift.getProfessionalId()).isEqualTo(UPDATED_PROFESSIONAL_ID);
        assertThat(testShift.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testShift.getNotes()).isEqualTo(UPDATED_NOTES);
    }

    @Test
    void putNonExistingShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(
                put(ENTITY_API_URL_ID, shift.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(shift))
            )
            .andExpect(status().isBadRequest());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(shift))
            )
            .andExpect(status().isBadRequest());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(shift)))
            .andExpect(status().isMethodNotAllowed());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateShiftWithPatch() throws Exception {
        shiftRepository.save(shift);

        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();

        Shift partialUpdatedShift = new Shift();
        partialUpdatedShift.setId(shift.getId());

        partialUpdatedShift.status(UPDATED_STATUS);

        restShiftMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedShift.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedShift))
            )
            .andExpect(status().isOk());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
        Shift testShift = shiftList.get(shiftList.size() - 1);
        assertThat(testShift.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testShift.getRosterId()).isEqualTo(DEFAULT_ROSTER_ID);
        assertThat(testShift.getNotes()).isEqualTo(DEFAULT_NOTES);
    }

    @Test
    void fullUpdateShiftWithPatch() throws Exception {
        shiftRepository.save(shift);

        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();

        Shift partialUpdatedShift = new Shift();
        partialUpdatedShift.setId(shift.getId());

        partialUpdatedShift
            .rosterId(UPDATED_ROSTER_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .startsAt(UPDATED_STARTS_AT)
            .endsAt(UPDATED_ENDS_AT)
            .status(UPDATED_STATUS)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restShiftMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedShift.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedShift))
            )
            .andExpect(status().isOk());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
        Shift testShift = shiftList.get(shiftList.size() - 1);
        assertThat(testShift.getRosterId()).isEqualTo(UPDATED_ROSTER_ID);
        assertThat(testShift.getProfessionalId()).isEqualTo(UPDATED_PROFESSIONAL_ID);
        assertThat(testShift.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testShift.getNotes()).isEqualTo(UPDATED_NOTES);
        assertThat(testShift.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, shift.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(shift))
            )
            .andExpect(status().isBadRequest());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(shift))
            )
            .andExpect(status().isBadRequest());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamShift() throws Exception {
        int databaseSizeBeforeUpdate = shiftRepository.findAll().size();
        shift.setId(UUID.randomUUID().toString());

        restShiftMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(shift)))
            .andExpect(status().isMethodNotAllowed());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteShift() throws Exception {
        shiftRepository.save(shift);

        int databaseSizeBeforeDelete = shiftRepository.findAll().size();

        restShiftMockMvc
            .perform(delete(ENTITY_API_URL_ID, shift.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        List<Shift> shiftList = shiftRepository.findAll();
        assertThat(shiftList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
