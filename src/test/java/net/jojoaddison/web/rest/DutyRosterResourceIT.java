package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.repository.DutyRosterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link DutyRosterResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
/*
 * Runs as ROLE_ADMIN. This entity is reference data: any authenticated caller may read it, but creating, updating
 * and deleting requires ROLE_ADMIN or ROLE_PROFESSIONAL. A default @WithMockUser is a ROLE_USER and would get 403
 * on every write here, which says nothing about the CRUD mechanics these tests exist to cover.
 *
 * The rule itself — that a patient can read but not write — is covered by ReferenceDataIT.
 */
@WithMockUser(authorities = { "ROLE_ADMIN" })
class DutyRosterResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_LOCATION = "AAAAAAAAAA";
    private static final String UPDATED_LOCATION = "BBBBBBBBBB";

    private static final Set<String> DEFAULT_SUBSCRIBED_PROFESSIONAL_IDS = Set.of("professional-a");
    private static final Set<String> UPDATED_SUBSCRIBED_PROFESSIONAL_IDS = Set.of("professional-b");

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now();

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now();

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/duty-rosters";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private DutyRosterRepository dutyRosterRepository;

    @Autowired
    private MockMvc restDutyRosterMockMvc;

    private DutyRoster dutyRoster;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DutyRoster createEntity() {
        return new DutyRoster()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .location(DEFAULT_LOCATION)
            .subscribedProfessionalIds(DEFAULT_SUBSCRIBED_PROFESSIONAL_IDS)
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
    public static DutyRoster createUpdatedEntity() {
        return new DutyRoster()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .location(UPDATED_LOCATION)
            .subscribedProfessionalIds(UPDATED_SUBSCRIBED_PROFESSIONAL_IDS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    public void initTest() {
        dutyRosterRepository.deleteAll();
        dutyRoster = createEntity();
    }

    @Test
    void createDutyRoster() throws Exception {
        int databaseSizeBeforeCreate = dutyRosterRepository.findAll().size();

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(dutyRoster)))
            .andExpect(status().isCreated());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeCreate + 1);
        DutyRoster testDutyRoster = dutyRosterList.get(dutyRosterList.size() - 1);
        assertThat(testDutyRoster.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testDutyRoster.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testDutyRoster.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testDutyRoster.getSubscribedProfessionalIds()).isEqualTo(DEFAULT_SUBSCRIBED_PROFESSIONAL_IDS);
        assertThat(testDutyRoster.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testDutyRoster.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testDutyRoster.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testDutyRoster.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createDutyRosterWithExistingId() throws Exception {
        dutyRoster.setId("existing_id");

        int databaseSizeBeforeCreate = dutyRosterRepository.findAll().size();

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(dutyRoster)))
            .andExpect(status().isBadRequest());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllDutyRosters() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dutyRoster.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].location").value(hasItem(DEFAULT_LOCATION)))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllDutyRostersFilteredByProfessional() throws Exception {
        dutyRosterRepository.save(dutyRoster);
        DutyRoster otherRoster = dutyRosterRepository.save(createUpdatedEntity());

        // The dashboard asks "which rosters does this clinician follow?", which is the only reason the filter exists.
        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL + "?professionalId={id}", "professional-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(dutyRoster.getId())))
            .andExpect(jsonPath("$.length()").value(1));

        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL + "?professionalId={id}", "professional-b"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherRoster.getId())))
            .andExpect(jsonPath("$.length()").value(1));

        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL + "?professionalId={id}", "professional-nobody"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getDutyRoster() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL_ID, dutyRoster.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dutyRoster.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.location").value(DEFAULT_LOCATION))
            .andExpect(jsonPath("$.subscribedProfessionalIds").value(hasItem("professional-a")));
    }

    @Test
    void getNonExistingDutyRoster() throws Exception {
        restDutyRosterMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingDutyRoster() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();

        DutyRoster updatedDutyRoster = dutyRosterRepository.findById(dutyRoster.getId()).orElseThrow();
        updatedDutyRoster
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .location(UPDATED_LOCATION)
            .subscribedProfessionalIds(UPDATED_SUBSCRIBED_PROFESSIONAL_IDS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedDutyRoster.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedDutyRoster))
            )
            .andExpect(status().isOk());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
        DutyRoster testDutyRoster = dutyRosterList.get(dutyRosterList.size() - 1);
        assertThat(testDutyRoster.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testDutyRoster.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testDutyRoster.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testDutyRoster.getSubscribedProfessionalIds()).isEqualTo(UPDATED_SUBSCRIBED_PROFESSIONAL_IDS);
        assertThat(testDutyRoster.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dutyRoster.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(dutyRoster))
            )
            .andExpect(status().isBadRequest());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(dutyRoster))
            )
            .andExpect(status().isBadRequest());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(dutyRoster)))
            .andExpect(status().isMethodNotAllowed());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateDutyRosterWithPatch() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();

        DutyRoster partialUpdatedDutyRoster = new DutyRoster();
        partialUpdatedDutyRoster.setId(dutyRoster.getId());

        partialUpdatedDutyRoster.name(UPDATED_NAME);

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDutyRoster.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedDutyRoster))
            )
            .andExpect(status().isOk());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
        DutyRoster testDutyRoster = dutyRosterList.get(dutyRosterList.size() - 1);
        assertThat(testDutyRoster.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testDutyRoster.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testDutyRoster.getLocation()).isEqualTo(DEFAULT_LOCATION);
        // A patch that does not mention the subscribers must not empty them — an omitted array and an empty one are
        // the same thing once deserialised, so the resource treats both as "leave alone".
        assertThat(testDutyRoster.getSubscribedProfessionalIds()).isEqualTo(DEFAULT_SUBSCRIBED_PROFESSIONAL_IDS);
    }

    @Test
    void fullUpdateDutyRosterWithPatch() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();

        DutyRoster partialUpdatedDutyRoster = new DutyRoster();
        partialUpdatedDutyRoster.setId(dutyRoster.getId());

        partialUpdatedDutyRoster
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .location(UPDATED_LOCATION)
            .subscribedProfessionalIds(UPDATED_SUBSCRIBED_PROFESSIONAL_IDS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDutyRoster.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedDutyRoster))
            )
            .andExpect(status().isOk());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
        DutyRoster testDutyRoster = dutyRosterList.get(dutyRosterList.size() - 1);
        assertThat(testDutyRoster.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testDutyRoster.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testDutyRoster.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testDutyRoster.getSubscribedProfessionalIds()).isEqualTo(UPDATED_SUBSCRIBED_PROFESSIONAL_IDS);
        assertThat(testDutyRoster.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dutyRoster.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(dutyRoster))
            )
            .andExpect(status().isBadRequest());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(dutyRoster))
            )
            .andExpect(status().isBadRequest());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamDutyRoster() throws Exception {
        int databaseSizeBeforeUpdate = dutyRosterRepository.findAll().size();
        dutyRoster.setId(UUID.randomUUID().toString());

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(dutyRoster))
            )
            .andExpect(status().isMethodNotAllowed());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteDutyRoster() throws Exception {
        dutyRosterRepository.save(dutyRoster);

        int databaseSizeBeforeDelete = dutyRosterRepository.findAll().size();

        restDutyRosterMockMvc
            .perform(delete(ENTITY_API_URL_ID, dutyRoster.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        List<DutyRoster> dutyRosterList = dutyRosterRepository.findAll();
        assertThat(dutyRosterList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
