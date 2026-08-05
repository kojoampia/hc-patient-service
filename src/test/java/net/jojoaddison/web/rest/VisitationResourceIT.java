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
import net.jojoaddison.domain.Visitation;
import net.jojoaddison.repository.VisitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link VisitationResource} REST controller.
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
class VisitationResourceIT {

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_CASE_ID = "AAAAAAAAAA";
    private static final String UPDATED_CASE_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_ID = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_VISITED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_VISITED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_PURPOSE = "AAAAAAAAAA";
    private static final String UPDATED_PURPOSE = "BBBBBBBBBB";

    private static final String DEFAULT_LOCATION = "AAAAAAAAAA";
    private static final String UPDATED_LOCATION = "BBBBBBBBBB";

    private static final String DEFAULT_NOTES = "AAAAAAAAAA";
    private static final String UPDATED_NOTES = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/visitations";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private VisitationRepository visitationRepository;

    @Autowired
    private MockMvc restVisitationMockMvc;

    private Visitation visitation;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Visitation createEntity() {
        Visitation visitation = new Visitation()
            .patientId(DEFAULT_PATIENT_ID)
            .caseId(DEFAULT_CASE_ID)
            .professionalId(DEFAULT_PROFESSIONAL_ID)
            .visitedAt(DEFAULT_VISITED_AT)
            .purpose(DEFAULT_PURPOSE)
            .location(DEFAULT_LOCATION)
            .notes(DEFAULT_NOTES)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return visitation;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Visitation createUpdatedEntity() {
        Visitation visitation = new Visitation()
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .visitedAt(UPDATED_VISITED_AT)
            .purpose(UPDATED_PURPOSE)
            .location(UPDATED_LOCATION)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return visitation;
    }

    @BeforeEach
    public void initTest() {
        visitationRepository.deleteAll();
        visitation = createEntity();
    }

    @Test
    void createVisitation() throws Exception {
        int databaseSizeBeforeCreate = visitationRepository.findAll().size();
        // Create the Visitation
        restVisitationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(visitation)))
            .andExpect(status().isCreated());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeCreate + 1);
        Visitation testVisitation = visitationList.get(visitationList.size() - 1);
        assertThat(testVisitation.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testVisitation.getCaseId()).isEqualTo(DEFAULT_CASE_ID);
        assertThat(testVisitation.getProfessionalId()).isEqualTo(DEFAULT_PROFESSIONAL_ID);
        assertThat(testVisitation.getVisitedAt()).isEqualTo(DEFAULT_VISITED_AT);
        assertThat(testVisitation.getPurpose()).isEqualTo(DEFAULT_PURPOSE);
        assertThat(testVisitation.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testVisitation.getNotes()).isEqualTo(DEFAULT_NOTES);
        assertThat(testVisitation.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testVisitation.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testVisitation.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testVisitation.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createVisitationWithExistingId() throws Exception {
        // Create the Visitation with an existing ID
        visitation.setId("existing_id");

        int databaseSizeBeforeCreate = visitationRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restVisitationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(visitation)))
            .andExpect(status().isBadRequest());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllVisitations() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        // Get all the visitationList
        restVisitationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(visitation.getId())))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].caseId").value(hasItem(DEFAULT_CASE_ID)))
            .andExpect(jsonPath("$.[*].professionalId").value(hasItem(DEFAULT_PROFESSIONAL_ID)))
            .andExpect(jsonPath("$.[*].visitedAt").value(hasItem(DEFAULT_VISITED_AT.toString())))
            .andExpect(jsonPath("$.[*].purpose").value(hasItem(DEFAULT_PURPOSE)))
            .andExpect(jsonPath("$.[*].location").value(hasItem(DEFAULT_LOCATION)))
            .andExpect(jsonPath("$.[*].notes").value(hasItem(DEFAULT_NOTES)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getAllVisitationsByPatientId() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        // The patient's own records come back
        restVisitationMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + DEFAULT_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(visitation.getId())));

        // Another patient's id returns nothing rather than everything
        restVisitationMockMvc
            .perform(get(ENTITY_API_URL + "?patientId=" + UPDATED_PATIENT_ID + "&sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getVisitation() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        // Get the visitation
        restVisitationMockMvc
            .perform(get(ENTITY_API_URL_ID, visitation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(visitation.getId()))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.caseId").value(DEFAULT_CASE_ID))
            .andExpect(jsonPath("$.professionalId").value(DEFAULT_PROFESSIONAL_ID))
            .andExpect(jsonPath("$.visitedAt").value(DEFAULT_VISITED_AT.toString()))
            .andExpect(jsonPath("$.purpose").value(DEFAULT_PURPOSE))
            .andExpect(jsonPath("$.location").value(DEFAULT_LOCATION))
            .andExpect(jsonPath("$.notes").value(DEFAULT_NOTES))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingVisitation() throws Exception {
        // Get the visitation
        restVisitationMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingVisitation() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();

        // Update the visitation
        Visitation updatedVisitation = visitationRepository.findById(visitation.getId()).orElseThrow();
        updatedVisitation
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .visitedAt(UPDATED_VISITED_AT)
            .purpose(UPDATED_PURPOSE)
            .location(UPDATED_LOCATION)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restVisitationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedVisitation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedVisitation))
            )
            .andExpect(status().isOk());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
        Visitation testVisitation = visitationList.get(visitationList.size() - 1);
        assertThat(testVisitation.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testVisitation.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testVisitation.getProfessionalId()).isEqualTo(UPDATED_PROFESSIONAL_ID);
        assertThat(testVisitation.getVisitedAt()).isEqualTo(UPDATED_VISITED_AT);
        assertThat(testVisitation.getPurpose()).isEqualTo(UPDATED_PURPOSE);
        assertThat(testVisitation.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testVisitation.getNotes()).isEqualTo(UPDATED_NOTES);
        assertThat(testVisitation.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testVisitation.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testVisitation.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testVisitation.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, visitation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(visitation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(visitation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(visitation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateVisitationWithPatch() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();

        // Update the visitation using partial update
        Visitation partialUpdatedVisitation = new Visitation();
        partialUpdatedVisitation.setId(visitation.getId());

        partialUpdatedVisitation
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .visitedAt(UPDATED_VISITED_AT)
            .purpose(UPDATED_PURPOSE);

        restVisitationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedVisitation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedVisitation))
            )
            .andExpect(status().isOk());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
        Visitation testVisitation = visitationList.get(visitationList.size() - 1);
        assertThat(testVisitation.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testVisitation.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testVisitation.getProfessionalId()).isEqualTo(DEFAULT_PROFESSIONAL_ID);
        assertThat(testVisitation.getVisitedAt()).isEqualTo(UPDATED_VISITED_AT);
        assertThat(testVisitation.getPurpose()).isEqualTo(UPDATED_PURPOSE);
        assertThat(testVisitation.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testVisitation.getNotes()).isEqualTo(DEFAULT_NOTES);
        assertThat(testVisitation.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testVisitation.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testVisitation.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testVisitation.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void fullUpdateVisitationWithPatch() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();

        // Update the visitation using partial update
        Visitation partialUpdatedVisitation = new Visitation();
        partialUpdatedVisitation.setId(visitation.getId());

        partialUpdatedVisitation
            .patientId(UPDATED_PATIENT_ID)
            .caseId(UPDATED_CASE_ID)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .visitedAt(UPDATED_VISITED_AT)
            .purpose(UPDATED_PURPOSE)
            .location(UPDATED_LOCATION)
            .notes(UPDATED_NOTES)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restVisitationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedVisitation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedVisitation))
            )
            .andExpect(status().isOk());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
        Visitation testVisitation = visitationList.get(visitationList.size() - 1);
        assertThat(testVisitation.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testVisitation.getCaseId()).isEqualTo(UPDATED_CASE_ID);
        assertThat(testVisitation.getProfessionalId()).isEqualTo(UPDATED_PROFESSIONAL_ID);
        assertThat(testVisitation.getVisitedAt()).isEqualTo(UPDATED_VISITED_AT);
        assertThat(testVisitation.getPurpose()).isEqualTo(UPDATED_PURPOSE);
        assertThat(testVisitation.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testVisitation.getNotes()).isEqualTo(UPDATED_NOTES);
        assertThat(testVisitation.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testVisitation.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testVisitation.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testVisitation.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, visitation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(visitation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(visitation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamVisitation() throws Exception {
        int databaseSizeBeforeUpdate = visitationRepository.findAll().size();
        visitation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVisitationMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(visitation))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Visitation in the database
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteVisitation() throws Exception {
        // Initialize the database
        visitationRepository.save(visitation);

        int databaseSizeBeforeDelete = visitationRepository.findAll().size();

        // Delete the visitation
        restVisitationMockMvc
            .perform(delete(ENTITY_API_URL_ID, visitation.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Visitation> visitationList = visitationRepository.findAll();
        assertThat(visitationList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
