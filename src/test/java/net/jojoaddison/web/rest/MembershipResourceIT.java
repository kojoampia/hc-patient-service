package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.MembershipAsserts.*;
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
import net.jojoaddison.domain.Membership;
import net.jojoaddison.repository.MembershipRepository;
import org.junit.jupiter.api.AfterEach;
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
    private ObjectMapper om;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MockMvc restMembershipMockMvc;

    private Membership membership;

    private Membership insertedMembership;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Membership createEntity() {
        return new Membership()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .status(DEFAULT_STATUS)
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
    public static Membership createUpdatedEntity() {
        return new Membership()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .status(UPDATED_STATUS)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    void initTest() {
        membership = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedMembership != null) {
            membershipRepository.delete(insertedMembership);
            insertedMembership = null;
        }
    }

    @Test
    void createMembership() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Membership
        var returnedMembership = om.readValue(
            restMembershipMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(membership)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Membership.class
        );

        // Validate the Membership in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertMembershipUpdatableFieldsEquals(returnedMembership, getPersistedMembership(returnedMembership));

        insertedMembership = returnedMembership;
    }

    @Test
    void createMembershipWithExistingId() throws Exception {
        // Create the Membership with an existing ID
        membership.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMembershipMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(membership)))
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMemberships() throws Exception {
        // Initialize the database
        insertedMembership = membershipRepository.save(membership);

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
        insertedMembership = membershipRepository.save(membership);

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
        insertedMembership = membershipRepository.save(membership);

        long databaseSizeBeforeUpdate = getRepositoryCount();

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
                    .content(om.writeValueAsBytes(updatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedMembershipToMatchAllProperties(updatedMembership);
    }

    @Test
    void putNonExistingMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                put(ENTITY_API_URL_ID, membership.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(membership)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMembershipWithPatch() throws Exception {
        // Initialize the database
        insertedMembership = membershipRepository.save(membership);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the membership using partial update
        Membership partialUpdatedMembership = new Membership();
        partialUpdatedMembership.setId(membership.getId());

        partialUpdatedMembership.name(UPDATED_NAME).status(UPDATED_STATUS).modifiedBy(UPDATED_MODIFIED_BY);

        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedMembership.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMembershipUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedMembership, membership),
            getPersistedMembership(membership)
        );
    }

    @Test
    void fullUpdateMembershipWithPatch() throws Exception {
        // Initialize the database
        insertedMembership = membershipRepository.save(membership);

        long databaseSizeBeforeUpdate = getRepositoryCount();

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
                    .content(om.writeValueAsBytes(partialUpdatedMembership))
            )
            .andExpect(status().isOk());

        // Validate the Membership in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMembershipUpdatableFieldsEquals(partialUpdatedMembership, getPersistedMembership(partialUpdatedMembership));
    }

    @Test
    void patchNonExistingMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, membership.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(membership))
            )
            .andExpect(status().isBadRequest());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMembership() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        membership.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMembershipMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(membership)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Membership in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMembership() throws Exception {
        // Initialize the database
        insertedMembership = membershipRepository.save(membership);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the membership
        restMembershipMockMvc
            .perform(delete(ENTITY_API_URL_ID, membership.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return membershipRepository.count();
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

    protected Membership getPersistedMembership(Membership membership) {
        return membershipRepository.findById(membership.getId()).orElseThrow();
    }

    protected void assertPersistedMembershipToMatchAllProperties(Membership expectedMembership) {
        assertMembershipAllPropertiesEquals(expectedMembership, getPersistedMembership(expectedMembership));
    }

    protected void assertPersistedMembershipToMatchUpdatableProperties(Membership expectedMembership) {
        assertMembershipAllUpdatablePropertiesEquals(expectedMembership, getPersistedMembership(expectedMembership));
    }
}
