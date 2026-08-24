package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.repository.CarePlanItemRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.CarePlanItemService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
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
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
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

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a CarePlanItem could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        carePlanItem.setArchivedAt(existing.getArchivedAt());

        carePlanItem.setArchivedById(existing.getArchivedById());

        carePlanItem.setArchiveReason(existing.getArchiveReason());

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
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
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
        patientScope.requireRead(ClinicalDomain.CARE_PLAN);
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
        patientScope.requireRead(ClinicalDomain.CARE_PLAN);
        Optional<CarePlanItem> carePlanItem = carePlanItemService
            .findOne(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(carePlanItem);
    }

    /**
     * {@code DELETE  /care-plan-items/:id} : delete the "id" carePlanItem.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the carePlanItem to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCarePlanItem(@PathVariable("id") String id) {
        log.debug("REST request to delete CarePlanItem : {}", id);
        if (carePlanItemRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        carePlanItemService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/care-plan-items/:id/archive} : retire a care plan item from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — CARE_PLAN — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a care plan item must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<CarePlanItem> archiveCarePlanItem(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        log.debug("REST request to archive CarePlanItem : {}", id);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (carePlanItemRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CarePlanItem archived = carePlanItemService.archive(id, professionalId(), reason.trim());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/care-plan-items/:id/unarchive} : put a care plan item back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<CarePlanItem> unarchiveCarePlanItem(@PathVariable("id") String id) {
        log.debug("REST request to unarchive CarePlanItem : {}", id);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        if (carePlanItemRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CarePlanItem restored = carePlanItemService.unarchive(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
