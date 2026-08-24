package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.repository.ActivityLogRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ActivityLogService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.ActivityLog}.
 */
@RestController
@RequestMapping("/api/activity-logs")
public class ActivityLogResource {

    private final Logger log = LoggerFactory.getLogger(ActivityLogResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceActivityLog";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ActivityLogService activityLogService;

    private final ActivityLogRepository activityLogRepository;

    private final PatientScope patientScope;

    public ActivityLogResource(
        ActivityLogService activityLogService,
        ActivityLogRepository activityLogRepository,
        PatientScope patientScope
    ) {
        this.activityLogService = activityLogService;
        this.activityLogRepository = activityLogRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /activity-logs} : Create a new activityLog.
     *
     * @param activityLog the activityLog to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new activityLog, or with status {@code 400 (Bad Request)} if the activityLog has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ActivityLog> createActivityLog(@RequestBody ActivityLog activityLog) throws URISyntaxException {
        log.debug("REST request to save ActivityLog : {}", activityLog);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (activityLog.getId() != null) {
            throw new BadRequestAlertException("A new activityLog cannot already have an ID", ENTITY_NAME, "idexists");
        }
        activityLog.setPatientId(patientScope.requirePatientIdForWrite(activityLog.getPatientId()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        activityLog.setCreatedBy(AuditStamp.currentUser());
        activityLog.setCreatedDate(AuditStamp.today());
        ActivityLog result = activityLogService.save(activityLog);
        return ResponseEntity
            .created(new URI("/api/activity-logs/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /activity-logs/:id} : Updates an existing activityLog.
     *
     * @param id the id of the activityLog to save.
     * @param activityLog the activityLog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated activityLog,
     * or with status {@code 400 (Bad Request)} if the activityLog is not valid,
     * or with status {@code 500 (Internal Server Error)} if the activityLog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActivityLog> updateActivityLog(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ActivityLog activityLog
    ) throws URISyntaxException {
        log.debug("REST request to update ActivityLog : {}, {}", id, activityLog);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (activityLog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, activityLog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        ActivityLog existing = activityLogRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        activityLog.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), activityLog.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        activityLog.setCreatedBy(existing.getCreatedBy());
        activityLog.setCreatedDate(existing.getCreatedDate());

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a ActivityLog could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        activityLog.setArchivedAt(existing.getArchivedAt());

        activityLog.setArchivedById(existing.getArchivedById());

        activityLog.setArchiveReason(existing.getArchiveReason());

        ActivityLog result = activityLogService.update(activityLog);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, activityLog.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /activity-logs/:id} : Partial updates given fields of an existing activityLog, field will ignore if it is null
     *
     * @param id the id of the activityLog to save.
     * @param activityLog the activityLog to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated activityLog,
     * or with status {@code 400 (Bad Request)} if the activityLog is not valid,
     * or with status {@code 404 (Not Found)} if the activityLog is not found,
     * or with status {@code 500 (Internal Server Error)} if the activityLog couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ActivityLog> partialUpdateActivityLog(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ActivityLog activityLog
    ) throws URISyntaxException {
        log.debug("REST request to partial update ActivityLog partially : {}, {}", id, activityLog);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (activityLog.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, activityLog.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        ActivityLog existing = activityLogRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        activityLog.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), activityLog.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        activityLog.setCreatedBy(existing.getCreatedBy());
        activityLog.setCreatedDate(existing.getCreatedDate());

        Optional<ActivityLog> result = activityLogService.partialUpdate(activityLog);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, activityLog.getId())
        );
    }

    /**
     * {@code GET  /activity-logs} : get all the activityLogs.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of activityLogs in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public ResponseEntity<List<ActivityLog>> getAllActivityLogs(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of ActivityLogs for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.ENCOUNTER);
        Page<ActivityLog> page = patientScope.findScopedPage(
            patientId,
            pageable,
            activityLogRepository::findAll,
            activityLogRepository::findByPatientId
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /activity-logs/:id} : get the "id" activityLog.
     *
     * @param id the id of the activityLog to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the activityLog, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActivityLog> getActivityLog(@PathVariable("id") String id) {
        log.debug("REST request to get ActivityLog : {}", id);
        patientScope.requireRead(ClinicalDomain.ENCOUNTER);
        Optional<ActivityLog> activityLog = activityLogService
            .findOne(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(activityLog);
    }

    /**
     * {@code DELETE  /activity-logs/:id} : delete the "id" activityLog.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the activityLog to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivityLog(@PathVariable("id") String id) {
        log.debug("REST request to delete ActivityLog : {}", id);
        if (activityLogRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        activityLogService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/activity-logs/:id/archive} : retire a activity log from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — ENCOUNTER — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a activity log must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<ActivityLog> archiveActivityLog(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        log.debug("REST request to archive ActivityLog : {}", id);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (activityLogRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ActivityLog archived = activityLogService.archive(id, professionalId(), reason.trim());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/activity-logs/:id/unarchive} : put a activity log back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<ActivityLog> unarchiveActivityLog(@PathVariable("id") String id) {
        log.debug("REST request to unarchive ActivityLog : {}", id);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (activityLogRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ActivityLog restored = activityLogService.unarchive(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
