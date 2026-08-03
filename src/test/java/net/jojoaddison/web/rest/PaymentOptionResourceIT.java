package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PaymentOption;
import net.jojoaddison.repository.PaymentOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PaymentOptionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class PaymentOptionResourceIT {

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_USER_ID = "AAAAAAAAAA";
    private static final String UPDATED_USER_ID = "BBBBBBBBBB";

    private static final String DEFAULT_METADATA = "AAAAAAAAAA";
    private static final String UPDATED_METADATA = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/payment-options";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private PaymentOptionRepository paymentOptionRepository;

    @Autowired
    private MockMvc restPaymentOptionMockMvc;

    private PaymentOption paymentOption;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PaymentOption createEntity() {
        PaymentOption paymentOption = new PaymentOption().type(DEFAULT_TYPE).userID(DEFAULT_USER_ID).metadata(DEFAULT_METADATA);
        return paymentOption;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PaymentOption createUpdatedEntity() {
        PaymentOption paymentOption = new PaymentOption().type(UPDATED_TYPE).userID(UPDATED_USER_ID).metadata(UPDATED_METADATA);
        return paymentOption;
    }

    @BeforeEach
    public void initTest() {
        paymentOptionRepository.deleteAll();
        paymentOption = createEntity();
    }

    @Test
    void createPaymentOption() throws Exception {
        int databaseSizeBeforeCreate = paymentOptionRepository.findAll().size();
        // Create the PaymentOption
        restPaymentOptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(paymentOption)))
            .andExpect(status().isCreated());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeCreate + 1);
        PaymentOption testPaymentOption = paymentOptionList.get(paymentOptionList.size() - 1);
        assertThat(testPaymentOption.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(testPaymentOption.getUserID()).isEqualTo(DEFAULT_USER_ID);
        assertThat(testPaymentOption.getMetadata()).isEqualTo(DEFAULT_METADATA);
    }

    @Test
    void createPaymentOptionWithExistingId() throws Exception {
        // Create the PaymentOption with an existing ID
        paymentOption.setId("existing_id");

        int databaseSizeBeforeCreate = paymentOptionRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPaymentOptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(paymentOption)))
            .andExpect(status().isBadRequest());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllPaymentOptions() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        // Get all the paymentOptionList
        restPaymentOptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(paymentOption.getId())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].userID").value(hasItem(DEFAULT_USER_ID)))
            .andExpect(jsonPath("$.[*].metadata").value(hasItem(DEFAULT_METADATA)));
    }

    @Test
    void getPaymentOption() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        // Get the paymentOption
        restPaymentOptionMockMvc
            .perform(get(ENTITY_API_URL_ID, paymentOption.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(paymentOption.getId()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.userID").value(DEFAULT_USER_ID))
            .andExpect(jsonPath("$.metadata").value(DEFAULT_METADATA));
    }

    @Test
    void getNonExistingPaymentOption() throws Exception {
        // Get the paymentOption
        restPaymentOptionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPaymentOption() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();

        // Update the paymentOption
        PaymentOption updatedPaymentOption = paymentOptionRepository.findById(paymentOption.getId()).orElseThrow();
        updatedPaymentOption.type(UPDATED_TYPE).userID(UPDATED_USER_ID).metadata(UPDATED_METADATA);

        restPaymentOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedPaymentOption.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedPaymentOption))
            )
            .andExpect(status().isOk());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
        PaymentOption testPaymentOption = paymentOptionList.get(paymentOptionList.size() - 1);
        assertThat(testPaymentOption.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testPaymentOption.getUserID()).isEqualTo(UPDATED_USER_ID);
        assertThat(testPaymentOption.getMetadata()).isEqualTo(UPDATED_METADATA);
    }

    @Test
    void putNonExistingPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, paymentOption.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(paymentOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(paymentOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(paymentOption)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePaymentOptionWithPatch() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();

        // Update the paymentOption using partial update
        PaymentOption partialUpdatedPaymentOption = new PaymentOption();
        partialUpdatedPaymentOption.setId(paymentOption.getId());

        partialUpdatedPaymentOption.type(UPDATED_TYPE).metadata(UPDATED_METADATA);

        restPaymentOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPaymentOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedPaymentOption))
            )
            .andExpect(status().isOk());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
        PaymentOption testPaymentOption = paymentOptionList.get(paymentOptionList.size() - 1);
        assertThat(testPaymentOption.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testPaymentOption.getUserID()).isEqualTo(DEFAULT_USER_ID);
        assertThat(testPaymentOption.getMetadata()).isEqualTo(UPDATED_METADATA);
    }

    @Test
    void fullUpdatePaymentOptionWithPatch() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();

        // Update the paymentOption using partial update
        PaymentOption partialUpdatedPaymentOption = new PaymentOption();
        partialUpdatedPaymentOption.setId(paymentOption.getId());

        partialUpdatedPaymentOption.type(UPDATED_TYPE).userID(UPDATED_USER_ID).metadata(UPDATED_METADATA);

        restPaymentOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPaymentOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedPaymentOption))
            )
            .andExpect(status().isOk());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
        PaymentOption testPaymentOption = paymentOptionList.get(paymentOptionList.size() - 1);
        assertThat(testPaymentOption.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testPaymentOption.getUserID()).isEqualTo(UPDATED_USER_ID);
        assertThat(testPaymentOption.getMetadata()).isEqualTo(UPDATED_METADATA);
    }

    @Test
    void patchNonExistingPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, paymentOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(paymentOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(paymentOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPaymentOption() throws Exception {
        int databaseSizeBeforeUpdate = paymentOptionRepository.findAll().size();
        paymentOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPaymentOptionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(paymentOption))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the PaymentOption in the database
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePaymentOption() throws Exception {
        // Initialize the database
        paymentOptionRepository.save(paymentOption);

        int databaseSizeBeforeDelete = paymentOptionRepository.findAll().size();

        // Delete the paymentOption
        restPaymentOptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, paymentOption.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<PaymentOption> paymentOptionList = paymentOptionRepository.findAll();
        assertThat(paymentOptionList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
