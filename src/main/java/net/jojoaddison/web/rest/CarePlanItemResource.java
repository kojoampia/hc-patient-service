package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.repository.CarePlanItemRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.CarePlanItemService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.CarePlanItem}.
 */
@RestController
@RequestMapping("/api/care-plan-items")
public class CarePlanItemResource {

    private final Logger log = LoggerFactory.getLogger(CarePlanItemResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceCarePlanItem";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CarePlanItemService carePlanItemService;

    private final CarePlanItemRepository carePlanItemRepository;

    private final PatientScope patientScope;

    public CarePlanItemResource(
        CarePlanItemService carePlanItemService,
        CarePlanItemRepository carePlanItemRepository,
        PatientScope patientScope
    ) {
        this.carePlanItemService = carePlanItemService;
        this.carePlanItemRepository = carePlanItemRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /care-plan-items} : Create a new carePlanItem.
     *
     * @param carePlanItem the carePlanItem to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new carePlanItem, or with status {@code 400 (Bad Request)} if the carePlanItem has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<CarePlanItem> createCarePlanItem(@RequestBody CarePlanItem carePlanItem) throws URISyntaxException {
        log.debug("REST request to save CarePlanItem : {}", carePlanItem);
        if (carePlanItem.getId() != null) {
            throw new BadRequestAlertException("A new carePlanItem cannot already have an ID", ENTITY_NAME, "idexists");
        }
        carePlanItem.setPatientId(patientScope.requirePatientIdForWrite(carePlanItem.getPatientId()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        carePlanItem.setCreatedBy(AuditStamp.currentUser());
        carePlanItem.setCreatedDate(AuditStamp.today());
        carePlanItem.setModifiedBy(AuditStamp.currentUser());
        carePlanItem.setModifiedDate(AuditStamp.today());
        CarePlanItem result = carePlanItemService.save(carePlanItem);
        return ResponseEntity
            .created(new URI("/api/care-plan-items/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /care-plan-items/:id} : Updates an existing carePlanItem.
     *
     * @param id the id of the carePlanItem to save.
     * @param carePlanItem the carePlanItem to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated carePlanItem,
     * or with status {@code 400 (Bad Request)} if the carePlanItem is not valid,
     * or with status {@code 500 (Internal Server Error)} if the carePlanItem couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CarePlanItem> updateCarePlanItem(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody CarePlanItem carePlanItem
    ) throws URISyntaxException {
        log.debug("REST request to update CarePlanItem : {}, {}", id, carePlanItem);
        if (carePlanItem.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, carePlanItem.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        CarePlanItem existing = carePlanItemRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        carePlanItem.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), carePlanItem.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        carePlanItem.setCreatedBy(existing.getCreatedBy());
        carePlanItem.setCreatedDate(existing.getCreatedDate());
        carePlanItem.setModifiedBy(AuditStamp.currentUser());
        carePlanItem.setModifiedDate(AuditStamp.today());

        CarePlanItem result = carePlanItemService.update(carePlanItem);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, carePlanItem.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /care-plan-items/:id} : Partial updates given fields of an existing carePlanItem, field will ignore if it is null
     *
     * @param id the id of the carePlanItem to save.
     * @param carePlanItem the carePlanItem to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated carePlanItem,
     * or with status {@code 400 (Bad Request)} if the carePlanItem is not valid,
     * or with status {@code 404 (Not Found)} if the carePlanItem is not found,
     * or with status {@code 500 (Internal Server Error)} if the carePlanItem couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<CarePlanItem> partialUpdateCarePlanItem(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody CarePlanItem carePlanItem
    ) throws URISyntaxException {
        log.debug("REST request to partial update CarePlanItem partially : {}, {}", id, carePlanItem);
        if (carePlanItem.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, carePlanItem.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        CarePlanItem existing = carePlanItemRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        carePlanItem.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), carePlanItem.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        carePlanItem.setCreatedBy(existing.getCreatedBy());
        carePlanItem.setCreatedDate(existing.getCreatedDate());
        carePlanItem.setModifiedBy(AuditStamp.currentUser());
        carePlanItem.setModifiedDate(AuditStamp.today());

        Optional<CarePlanItem> result = carePlanItemService.partialUpdate(carePlanItem);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, carePlanItem.getId())
        );
    }

    /**
     * {@code GET  /care-plan-items} : get all the carePlanItems.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of carePlanItems in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<CarePlanItem> getAllCarePlanItems(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all CarePlanItems for patient {}", patientId);
        return patientScope.findScoped(patientId, carePlanItemRepository::findAll, carePlanItemRepository::findByPatientId);
    }

    /**
     * {@code GET  /care-plan-items/:id} : get the "id" carePlanItem.
     *
     * @param id the id of the carePlanItem to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the carePlanItem, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CarePlanItem> getCarePlanItem(@PathVariable("id") String id) {
        log.debug("REST request to get CarePlanItem : {}", id);
        Optional<CarePlanItem> carePlanItem = carePlanItemService
            .findOne(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(carePlanItem);
    }

    /**
     * {@code DELETE  /care-plan-items/:id} : delete the "id" carePlanItem.
     *
     * @param id the id of the carePlanItem to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCarePlanItem(@PathVariable("id") String id) {
        log.debug("REST request to delete CarePlanItem : {}", id);
        if (carePlanItemRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        carePlanItemService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
