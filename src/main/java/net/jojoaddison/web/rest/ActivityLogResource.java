package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.repository.ActivityLogRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.ActivityLogService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
        Optional<ActivityLog> activityLog = activityLogService
            .findOne(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(activityLog);
    }

    /**
     * {@code DELETE  /activity-logs/:id} : delete the "id" activityLog.
     *
     * @param id the id of the activityLog to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivityLog(@PathVariable("id") String id) {
        log.debug("REST request to delete ActivityLog : {}", id);
        if (activityLogRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        activityLogService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
