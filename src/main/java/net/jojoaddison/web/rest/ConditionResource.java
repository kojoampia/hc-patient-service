package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Condition;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ArchiveSupport;
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
 * REST controller for managing {@link net.jojoaddison.domain.Condition}.
 */
@RestController
@RequestMapping("/api/conditions")
public class ConditionResource {

    private final Logger log = LoggerFactory.getLogger(ConditionResource.class);

    private static final String ENTITY_NAME = "patientMsCondition";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ConditionRepository conditionRepository;

    private final PatientScope patientScope;

    public ConditionResource(ConditionRepository conditionRepository, PatientScope patientScope) {
        this.conditionRepository = conditionRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /conditions} : Create a new condition.
     *
     * @param condition the condition to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new condition, or with status {@code 400 (Bad Request)} if the condition has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Condition> createCondition(@RequestBody Condition condition) throws URISyntaxException {
        log.debug("REST request to save Condition : {}", condition);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (condition.getId() != null) {
            throw new BadRequestAlertException("A new condition cannot already have an ID", ENTITY_NAME, "idexists");
        }
        condition.setPatientId(patientScope.requirePatientIdForWrite(condition.getPatientId()));
        // Provenance comes from the caller, never from the body — otherwise anyone could post a record
        // marked PROFESSIONAL and have it read as clinician-attested ever after.
        condition.setSource(patientScope.currentActivitySource());
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        condition.setCreatedBy(AuditStamp.currentUser());
        condition.setCreatedDate(AuditStamp.today());
        condition.setModifiedBy(AuditStamp.currentUser());
        condition.setModifiedDate(AuditStamp.today());
        Condition result = conditionRepository.save(condition);
        return ResponseEntity
            .created(new URI("/api/conditions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /conditions/:id} : Updates an existing condition.
     *
     * @param id the id of the condition to save.
     * @param condition the condition to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated condition,
     * or with status {@code 400 (Bad Request)} if the condition is not valid,
     * or with status {@code 500 (Internal Server Error)} if the condition couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Condition> updateCondition(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Condition condition
    ) throws URISyntaxException {
        log.debug("REST request to update Condition : {}, {}", id, condition);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (condition.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, condition.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Condition existing = conditionRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        condition.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), condition.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        condition.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        condition.setCreatedBy(existing.getCreatedBy());
        condition.setCreatedDate(existing.getCreatedDate());
        condition.setModifiedBy(AuditStamp.currentUser());
        condition.setModifiedDate(AuditStamp.today());

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a Condition could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        condition.setArchivedAt(existing.getArchivedAt());

        condition.setArchivedById(existing.getArchivedById());

        condition.setArchiveReason(existing.getArchiveReason());

        Condition result = conditionRepository.save(condition);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, condition.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /conditions/:id} : Partial updates given fields of an existing condition, field will ignore if it is null
     *
     * @param id the id of the condition to save.
     * @param condition the condition to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated condition,
     * or with status {@code 400 (Bad Request)} if the condition is not valid,
     * or with status {@code 404 (Not Found)} if the condition is not found,
     * or with status {@code 500 (Internal Server Error)} if the condition couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Condition> partialUpdateCondition(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Condition condition
    ) throws URISyntaxException {
        log.debug("REST request to partial update Condition partially : {}, {}", id, condition);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (condition.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, condition.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Condition existing = conditionRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        condition.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), condition.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        condition.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        condition.setCreatedBy(existing.getCreatedBy());
        condition.setCreatedDate(existing.getCreatedDate());
        condition.setModifiedBy(AuditStamp.currentUser());
        condition.setModifiedDate(AuditStamp.today());

        Optional<Condition> result = conditionRepository
            .findById(condition.getId())
            .map(existingCondition -> {
                if (condition.getName() != null) {
                    existingCondition.setName(condition.getName());
                }
                if (condition.getDescription() != null) {
                    existingCondition.setDescription(condition.getDescription());
                }
                if (condition.getPatientId() != null) {
                    existingCondition.setPatientId(condition.getPatientId());
                }
                if (condition.getCreatedDate() != null) {
                    existingCondition.setCreatedDate(condition.getCreatedDate());
                }
                if (condition.getModifiedDate() != null) {
                    existingCondition.setModifiedDate(condition.getModifiedDate());
                }
                if (condition.getCreatedBy() != null) {
                    existingCondition.setCreatedBy(condition.getCreatedBy());
                }
                if (condition.getModifiedBy() != null) {
                    existingCondition.setModifiedBy(condition.getModifiedBy());
                }

                return existingCondition;
            })
            .map(conditionRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, condition.getId())
        );
    }

    /**
     * {@code GET  /conditions} : get all the conditions.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of conditions in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<Condition> getAllConditions(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all Conditions for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.DIAGNOSIS);
        return patientScope.findScoped(patientId, conditionRepository::findAll, conditionRepository::findByPatientId);
    }

    /**
     * {@code GET  /conditions/:id} : get the "id" condition.
     *
     * @param id the id of the condition to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the condition, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Condition> getCondition(@PathVariable("id") String id) {
        log.debug("REST request to get Condition : {}", id);
        patientScope.requireRead(ClinicalDomain.DIAGNOSIS);
        Optional<Condition> condition = conditionRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(condition);
    }

    /**
     * {@code DELETE  /conditions/:id} : delete the "id" condition.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the condition to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCondition(@PathVariable("id") String id) {
        log.debug("REST request to delete Condition : {}", id);
        if (conditionRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        conditionRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/conditions/:id/archive} : retire a condition from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — DIAGNOSIS — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a condition must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Condition> archiveCondition(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        log.debug("REST request to archive Condition : {}", id);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (conditionRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Condition archived = ArchiveSupport.archive(
            conditionRepository.findById(id),
            id,
            professionalId(),
            reason.trim(),
            ENTITY_NAME,
            "condition",
            conditionRepository::save
        );
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/conditions/:id/unarchive} : put a condition back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Condition> unarchiveCondition(@PathVariable("id") String id) {
        log.debug("REST request to unarchive Condition : {}", id);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (conditionRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Condition restored = ArchiveSupport.unarchive(
            conditionRepository.findById(id),
            id,
            ENTITY_NAME,
            "condition",
            conditionRepository::save
        );
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
