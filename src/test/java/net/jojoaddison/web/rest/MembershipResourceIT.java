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
import net.jojoaddison.domain.Membership;
import net.jojoaddison.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link MembershipResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class MembershipResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/memberships";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MockMvc restMembershipMockMvc;

    private Membership membership;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Membership createEntity() {
        Membership membership = new Membership()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .status(DEFAULT_STATUS)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return membership;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Membership createUpdatedEntity() {
        Membership membership = new Membership()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return membership;
    }

    @BeforeEach
    public void initTest() {
        membershipRepository.deleteAll();
        membership = createEntity();
    }

    @Test
    void createMembership() throws Exception {
        int databaseSizeBeforeCreate = membershipRepository.findAll().size();
        // Create the Membership
        restMembershipMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(membership)))
            .andExpect(status().isCreated());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeCreate + 1);
        Membership testMembership = membershipList.get(membershipList.size() - 1);
        assertThat(testMembership.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testMembership.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testMembership.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testMembership.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testMembership.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testMembership.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testMembership.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createMembershipWithExistingId() throws Exception {
        // Create the Membership with an existing ID
        membership.setId("existing_id");

        int databaseSizeBeforeCreate = membershipRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMembershipMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(membership)))
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMemberships() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        // Get all the membershipList
        restMembershipMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(membership.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getMembership() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        // Get the membership
        restMembershipMockMvc
            .perform(get(ENTITY_API_URL_ID, membership.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(membership.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingMembership() throws Exception {
        // Get the membership
        restMembershipMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingMembership() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();

        // Update the membership
        Membership updatedMembership = membershipRepository.findById(membership.getId()).orElseThrow();
        updatedMembership
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMembershipMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedMembership.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
        Membership testMembership = membershipList.get(membershipList.size() - 1);
        assertThat(testMembership.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMembership.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testMembership.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testMembership.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testMembership.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testMembership.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testMembership.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                put(ENTITY_API_URL_ID, membership.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(membership)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMembershipWithPatch() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();

        // Update the membership using partial update
        Membership partialUpdatedMembership = new Membership();
        partialUpdatedMembership.setId(membership.getId());

        partialUpdatedMembership
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMembership.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
        Membership testMembership = membershipList.get(membershipList.size() - 1);
        assertThat(testMembership.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMembership.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testMembership.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testMembership.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testMembership.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testMembership.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testMembership.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateMembershipWithPatch() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();

        // Update the membership using partial update
        Membership partialUpdatedMembership = new Membership();
        partialUpdatedMembership.setId(membership.getId());

        partialUpdatedMembership
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMembership.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
        Membership testMembership = membershipList.get(membershipList.size() - 1);
        assertThat(testMembership.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testMembership.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testMembership.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testMembership.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testMembership.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testMembership.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testMembership.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, membership.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMembership() throws Exception {
        int databaseSizeBeforeUpdate = membershipRepository.findAll().size();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(membership))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Membership in the database
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMembership() throws Exception {
        // Initialize the database
        membershipRepository.save(membership);

        int databaseSizeBeforeDelete = membershipRepository.findAll().size();

        // Delete the membership
        restMembershipMockMvc
            .perform(delete(ENTITY_API_URL_ID, membership.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Membership> membershipList = membershipRepository.findAll();
        assertThat(membershipList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
