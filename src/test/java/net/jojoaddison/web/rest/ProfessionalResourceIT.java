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
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ProfessionalResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
/*
 * Runs as ROLE_ADMIN. This entity is reference data since 2026-08-05: any authenticated caller may read it,
 * but creating, updating and deleting requires ROLE_ADMIN or ROLE_PROFESSIONAL. A default @WithMockUser is a
 * ROLE_USER and would now get 403 on every write here, which says nothing about the CRUD mechanics these
 * tests exist to cover.
 *
 * The rule itself — that a patient can read but not write — is covered by ReferenceDataIT.
 */
@WithMockUser(authorities = { "ROLE_ADMIN" })
class ProfessionalResourceIT {

    private static final String DEFAULT_FIRST_NAME = "AAAAAAAAAA";
    private static final String UPDATED_FIRST_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_LAST_NAME = "AAAAAAAAAA";
    private static final String UPDATED_LAST_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_HONORIFIC = "Dr.";
    private static final String UPDATED_HONORIFIC = "Prof.";

    private static final String DEFAULT_ROLE = "AAAAAAAAAA";
    private static final String UPDATED_ROLE = "BBBBBBBBBB";

    private static final String DEFAULT_SPECIALTY = "AAAAAAAAAA";
    private static final String UPDATED_SPECIALTY = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_PHONE_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_PHONE_NUMBER = "BBBBBBBBBB";

    private static final String DEFAULT_IMAGE_URL = "AAAAAAAAAA";
    private static final String UPDATED_IMAGE_URL = "BBBBBBBBBB";

    private static final String DEFAULT_INITIALS = "AAAAAAAAAA";
    private static final String UPDATED_INITIALS = "BBBBBBBBBB";

    private static final String DEFAULT_LOCATION = "AAAAAAAAAA";
    private static final String UPDATED_LOCATION = "BBBBBBBBBB";

    private static final String DEFAULT_TEAM_ID = "AAAAAAAAAA";
    private static final String UPDATED_TEAM_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/professionals";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private MockMvc restProfessionalMockMvc;

    private Professional professional;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Professional createEntity() {
        Professional professional = new Professional()
            .firstName(DEFAULT_FIRST_NAME)
            .lastName(DEFAULT_LAST_NAME)
            .honorific(DEFAULT_HONORIFIC)
            .role(DEFAULT_ROLE)
            .specialty(DEFAULT_SPECIALTY)
            .email(DEFAULT_EMAIL)
            .phoneNumber(DEFAULT_PHONE_NUMBER)
            .imageUrl(DEFAULT_IMAGE_URL)
            .initials(DEFAULT_INITIALS)
            .location(DEFAULT_LOCATION)
            .teamId(DEFAULT_TEAM_ID)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return professional;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Professional createUpdatedEntity() {
        Professional professional = new Professional()
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .honorific(UPDATED_HONORIFIC)
            .role(UPDATED_ROLE)
            .specialty(UPDATED_SPECIALTY)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .imageUrl(UPDATED_IMAGE_URL)
            .initials(UPDATED_INITIALS)
            .location(UPDATED_LOCATION)
            .teamId(UPDATED_TEAM_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return professional;
    }

    @BeforeEach
    public void initTest() {
        professionalRepository.deleteAll();
        professional = createEntity();
    }

    @Test
    void createProfessional() throws Exception {
        int databaseSizeBeforeCreate = professionalRepository.findAll().size();
        // Create the Professional
        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(professional)))
            .andExpect(status().isCreated());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeCreate + 1);
        Professional testProfessional = professionalList.get(professionalList.size() - 1);
        assertThat(testProfessional.getFirstName()).isEqualTo(DEFAULT_FIRST_NAME);
        assertThat(testProfessional.getLastName()).isEqualTo(DEFAULT_LAST_NAME);
        assertThat(testProfessional.getHonorific()).isEqualTo(DEFAULT_HONORIFIC);
        assertThat(testProfessional.getRole()).isEqualTo(DEFAULT_ROLE);
        assertThat(testProfessional.getSpecialty()).isEqualTo(DEFAULT_SPECIALTY);
        assertThat(testProfessional.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(testProfessional.getPhoneNumber()).isEqualTo(DEFAULT_PHONE_NUMBER);
        assertThat(testProfessional.getImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);
        assertThat(testProfessional.getInitials()).isEqualTo(DEFAULT_INITIALS);
        assertThat(testProfessional.getLocation()).isEqualTo(DEFAULT_LOCATION);
        assertThat(testProfessional.getTeamId()).isEqualTo(DEFAULT_TEAM_ID);
        assertThat(testProfessional.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testProfessional.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testProfessional.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testProfessional.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createProfessionalWithExistingId() throws Exception {
        // Create the Professional with an existing ID
        professional.setId("existing_id");

        int databaseSizeBeforeCreate = professionalRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(professional)))
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllProfessionals() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        // Get all the professionalList
        restProfessionalMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(professional.getId())))
            .andExpect(jsonPath("$.[*].firstName").value(hasItem(DEFAULT_FIRST_NAME)))
            .andExpect(jsonPath("$.[*].lastName").value(hasItem(DEFAULT_LAST_NAME)))
            .andExpect(jsonPath("$.[*].honorific").value(hasItem(DEFAULT_HONORIFIC)))
            .andExpect(jsonPath("$.[*].role").value(hasItem(DEFAULT_ROLE)))
            .andExpect(jsonPath("$.[*].specialty").value(hasItem(DEFAULT_SPECIALTY)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].phoneNumber").value(hasItem(DEFAULT_PHONE_NUMBER)))
            .andExpect(jsonPath("$.[*].imageUrl").value(hasItem(DEFAULT_IMAGE_URL)))
            .andExpect(jsonPath("$.[*].initials").value(hasItem(DEFAULT_INITIALS)))
            .andExpect(jsonPath("$.[*].location").value(hasItem(DEFAULT_LOCATION)))
            .andExpect(jsonPath("$.[*].teamId").value(hasItem(DEFAULT_TEAM_ID)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getProfessional() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        // Get the professional
        restProfessionalMockMvc
            .perform(get(ENTITY_API_URL_ID, professional.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(professional.getId()))
            .andExpect(jsonPath("$.firstName").value(DEFAULT_FIRST_NAME))
            .andExpect(jsonPath("$.lastName").value(DEFAULT_LAST_NAME))
            .andExpect(jsonPath("$.honorific").value(DEFAULT_HONORIFIC))
            .andExpect(jsonPath("$.role").value(DEFAULT_ROLE))
            .andExpect(jsonPath("$.specialty").value(DEFAULT_SPECIALTY))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.phoneNumber").value(DEFAULT_PHONE_NUMBER))
            .andExpect(jsonPath("$.imageUrl").value(DEFAULT_IMAGE_URL))
            .andExpect(jsonPath("$.initials").value(DEFAULT_INITIALS))
            .andExpect(jsonPath("$.location").value(DEFAULT_LOCATION))
            .andExpect(jsonPath("$.teamId").value(DEFAULT_TEAM_ID))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingProfessional() throws Exception {
        // Get the professional
        restProfessionalMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingProfessional() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();

        // Update the professional
        Professional updatedProfessional = professionalRepository.findById(professional.getId()).orElseThrow();
        updatedProfessional
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .honorific(UPDATED_HONORIFIC)
            .role(UPDATED_ROLE)
            .specialty(UPDATED_SPECIALTY)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .imageUrl(UPDATED_IMAGE_URL)
            .initials(UPDATED_INITIALS)
            .location(UPDATED_LOCATION)
            .teamId(UPDATED_TEAM_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedProfessional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
        Professional testProfessional = professionalList.get(professionalList.size() - 1);
        assertThat(testProfessional.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(testProfessional.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testProfessional.getHonorific()).isEqualTo(UPDATED_HONORIFIC);
        assertThat(testProfessional.getRole()).isEqualTo(UPDATED_ROLE);
        assertThat(testProfessional.getSpecialty()).isEqualTo(UPDATED_SPECIALTY);
        assertThat(testProfessional.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testProfessional.getPhoneNumber()).isEqualTo(UPDATED_PHONE_NUMBER);
        assertThat(testProfessional.getImageUrl()).isEqualTo(UPDATED_IMAGE_URL);
        assertThat(testProfessional.getInitials()).isEqualTo(UPDATED_INITIALS);
        assertThat(testProfessional.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testProfessional.getTeamId()).isEqualTo(UPDATED_TEAM_ID);
        assertThat(testProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testProfessional.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testProfessional.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, professional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(professional)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateProfessionalWithPatch() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();

        // Update the professional using partial update
        Professional partialUpdatedProfessional = new Professional();
        partialUpdatedProfessional.setId(professional.getId());

        partialUpdatedProfessional
            .lastName(UPDATED_LAST_NAME)
            .email(UPDATED_EMAIL)
            .location(UPDATED_LOCATION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
        Professional testProfessional = professionalList.get(professionalList.size() - 1);
        assertThat(testProfessional.getFirstName()).isEqualTo(DEFAULT_FIRST_NAME);
        assertThat(testProfessional.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testProfessional.getHonorific()).isEqualTo(DEFAULT_HONORIFIC);
        assertThat(testProfessional.getRole()).isEqualTo(DEFAULT_ROLE);
        assertThat(testProfessional.getSpecialty()).isEqualTo(DEFAULT_SPECIALTY);
        assertThat(testProfessional.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testProfessional.getPhoneNumber()).isEqualTo(DEFAULT_PHONE_NUMBER);
        assertThat(testProfessional.getImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);
        assertThat(testProfessional.getInitials()).isEqualTo(DEFAULT_INITIALS);
        assertThat(testProfessional.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testProfessional.getTeamId()).isEqualTo(DEFAULT_TEAM_ID);
        assertThat(testProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testProfessional.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testProfessional.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateProfessionalWithPatch() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();

        // Update the professional using partial update
        Professional partialUpdatedProfessional = new Professional();
        partialUpdatedProfessional.setId(professional.getId());

        partialUpdatedProfessional
            .firstName(UPDATED_FIRST_NAME)
            .lastName(UPDATED_LAST_NAME)
            .honorific(UPDATED_HONORIFIC)
            .role(UPDATED_ROLE)
            .specialty(UPDATED_SPECIALTY)
            .email(UPDATED_EMAIL)
            .phoneNumber(UPDATED_PHONE_NUMBER)
            .imageUrl(UPDATED_IMAGE_URL)
            .initials(UPDATED_INITIALS)
            .location(UPDATED_LOCATION)
            .teamId(UPDATED_TEAM_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedProfessional))
            )
            .andExpect(status().isOk());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
        Professional testProfessional = professionalList.get(professionalList.size() - 1);
        assertThat(testProfessional.getFirstName()).isEqualTo(UPDATED_FIRST_NAME);
        assertThat(testProfessional.getLastName()).isEqualTo(UPDATED_LAST_NAME);
        assertThat(testProfessional.getHonorific()).isEqualTo(UPDATED_HONORIFIC);
        assertThat(testProfessional.getRole()).isEqualTo(UPDATED_ROLE);
        assertThat(testProfessional.getSpecialty()).isEqualTo(UPDATED_SPECIALTY);
        assertThat(testProfessional.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testProfessional.getPhoneNumber()).isEqualTo(UPDATED_PHONE_NUMBER);
        assertThat(testProfessional.getImageUrl()).isEqualTo(UPDATED_IMAGE_URL);
        assertThat(testProfessional.getInitials()).isEqualTo(UPDATED_INITIALS);
        assertThat(testProfessional.getLocation()).isEqualTo(UPDATED_LOCATION);
        assertThat(testProfessional.getTeamId()).isEqualTo(UPDATED_TEAM_ID);
        assertThat(testProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testProfessional.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testProfessional.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, professional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(professional))
            )
            .andExpect(status().isBadRequest());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamProfessional() throws Exception {
        int databaseSizeBeforeUpdate = professionalRepository.findAll().size();
        professional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(professional))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Professional in the database
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteProfessional() throws Exception {
        // Initialize the database
        professionalRepository.save(professional);

        int databaseSizeBeforeDelete = professionalRepository.findAll().size();

        // Delete the professional
        restProfessionalMockMvc
            .perform(delete(ENTITY_API_URL_ID, professional.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Professional> professionalList = professionalRepository.findAll();
        assertThat(professionalList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
