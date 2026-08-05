package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.PaymentOption;
import net.jojoaddison.repository.PaymentOptionRepository;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PaymentOption}.
 */
@RestController
@RequestMapping("/api/payment-options")
public class PaymentOptionResource {

    private final Logger log = LoggerFactory.getLogger(PaymentOptionResource.class);

    private static final String ENTITY_NAME = "hcPatientServicePaymentOption";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PaymentOptionRepository paymentOptionRepository;

    private final PatientScope patientScope;

    public PaymentOptionResource(PaymentOptionRepository paymentOptionRepository, PatientScope patientScope) {
        this.paymentOptionRepository = paymentOptionRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /payment-options} : Create a new paymentOption.
     *
     * @param paymentOption the paymentOption to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new paymentOption, or with status {@code 400 (Bad Request)} if the paymentOption has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PaymentOption> createPaymentOption(@RequestBody PaymentOption paymentOption) throws URISyntaxException {
        log.debug("REST request to save PaymentOption : {}", paymentOption);
        if (paymentOption.getId() != null) {
            throw new BadRequestAlertException("A new paymentOption cannot already have an ID", ENTITY_NAME, "idexists");
        }
        paymentOption.setUserID(patientScope.requirePatientIdForWrite(paymentOption.getUserID()));
        PaymentOption result = paymentOptionRepository.save(paymentOption);
        return ResponseEntity
            .created(new URI("/api/payment-options/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /payment-options/:id} : Updates an existing paymentOption.
     *
     * @param id the id of the paymentOption to save.
     * @param paymentOption the paymentOption to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated paymentOption,
     * or with status {@code 400 (Bad Request)} if the paymentOption is not valid,
     * or with status {@code 500 (Internal Server Error)} if the paymentOption couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PaymentOption> updatePaymentOption(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody PaymentOption paymentOption
    ) throws URISyntaxException {
        log.debug("REST request to update PaymentOption : {}, {}", id, paymentOption);
        if (paymentOption.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, paymentOption.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        PaymentOption existing = paymentOptionRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getUserID()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload.
        paymentOption.setUserID(patientScope.patientIdForUpdate(existing.getUserID(), paymentOption.getUserID()));

        PaymentOption result = paymentOptionRepository.save(paymentOption);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, paymentOption.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /payment-options/:id} : Partial updates given fields of an existing paymentOption, field will ignore if it is null
     *
     * @param id the id of the paymentOption to save.
     * @param paymentOption the paymentOption to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated paymentOption,
     * or with status {@code 400 (Bad Request)} if the paymentOption is not valid,
     * or with status {@code 404 (Not Found)} if the paymentOption is not found,
     * or with status {@code 500 (Internal Server Error)} if the paymentOption couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PaymentOption> partialUpdatePaymentOption(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody PaymentOption paymentOption
    ) throws URISyntaxException {
        log.debug("REST request to partial update PaymentOption partially : {}, {}", id, paymentOption);
        if (paymentOption.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, paymentOption.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        PaymentOption existing = paymentOptionRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getUserID()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload.
        paymentOption.setUserID(patientScope.patientIdForUpdate(existing.getUserID(), paymentOption.getUserID()));

        Optional<PaymentOption> result = paymentOptionRepository
            .findById(paymentOption.getId())
            .map(existingPaymentOption -> {
                if (paymentOption.getType() != null) {
                    existingPaymentOption.setType(paymentOption.getType());
                }
                if (paymentOption.getUserID() != null) {
                    existingPaymentOption.setUserID(paymentOption.getUserID());
                }
                if (paymentOption.getMetadata() != null) {
                    existingPaymentOption.setMetadata(paymentOption.getMetadata());
                }

                return existingPaymentOption;
            })
            .map(paymentOptionRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, paymentOption.getId())
        );
    }

    /**
     * {@code GET  /payment-options} : get all the paymentOptions.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of paymentOptions in body.
     */
    @GetMapping("")
    public List<PaymentOption> getAllPaymentOptions(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all PaymentOptions");
        return patientScope.findScoped(patientId, paymentOptionRepository::findAll, paymentOptionRepository::findByUserID);
    }

    /**
     * {@code GET  /payment-options/:id} : get the "id" paymentOption.
     *
     * @param id the id of the paymentOption to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the paymentOption, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentOption> getPaymentOption(@PathVariable("id") String id) {
        log.debug("REST request to get PaymentOption : {}", id);
        Optional<PaymentOption> paymentOption = paymentOptionRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getUserID()));
        return ResponseUtil.wrapOrNotFound(paymentOption);
    }

    /**
     * {@code DELETE  /payment-options/:id} : delete the "id" paymentOption.
     *
     * @param id the id of the paymentOption to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaymentOption(@PathVariable("id") String id) {
        log.debug("REST request to delete PaymentOption : {}", id);
        if (paymentOptionRepository.findById(id).filter(current -> patientScope.isVisible(current.getUserID())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        paymentOptionRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
