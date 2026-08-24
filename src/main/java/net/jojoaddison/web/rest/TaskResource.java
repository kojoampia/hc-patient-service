package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Task;
import net.jojoaddison.repository.TaskRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.Task}.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskResource {

    private final Logger log = LoggerFactory.getLogger(TaskResource.class);

    private static final String ENTITY_NAME = "patientMsTask";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TaskRepository taskRepository;

    private final PatientScope patientScope;

    public TaskResource(TaskRepository taskRepository, PatientScope patientScope) {
        this.taskRepository = taskRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /tasks} : Create a new task.
     *
     * @param task the task to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new task, or with status {@code 400 (Bad Request)} if the task has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Task> createTask(@RequestBody Task task) throws URISyntaxException {
        log.debug("REST request to save Task : {}", task);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        if (task.getId() != null) {
            throw new BadRequestAlertException("A new task cannot already have an ID", ENTITY_NAME, "idexists");
        }
        task.setPatientId(patientScope.requirePatientIdForWrite(task.getPatientId()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        task.setCreatedBy(AuditStamp.currentUser());
        task.setCreatedDate(AuditStamp.today());
        task.setModifiedBy(AuditStamp.currentUser());
        task.setModifiedDate(AuditStamp.today());
        Task result = taskRepository.save(task);
        return ResponseEntity
            .created(new URI("/api/tasks/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /tasks/:id} : Updates an existing task.
     *
     * @param id the id of the task to save.
     * @param task the task to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated task,
     * or with status {@code 400 (Bad Request)} if the task is not valid,
     * or with status {@code 500 (Internal Server Error)} if the task couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable(value = "id", required = false) final String id, @RequestBody Task task)
        throws URISyntaxException {
        log.debug("REST request to update Task : {}, {}", id, task);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        if (task.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, task.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Task existing = taskRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        task.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), task.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        task.setCreatedBy(existing.getCreatedBy());
        task.setCreatedDate(existing.getCreatedDate());
        task.setModifiedBy(AuditStamp.currentUser());
        task.setModifiedDate(AuditStamp.today());

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a Task could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        task.setArchivedAt(existing.getArchivedAt());

        task.setArchivedById(existing.getArchivedById());

        task.setArchiveReason(existing.getArchiveReason());

        Task result = taskRepository.save(task);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, task.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /tasks/:id} : Partial updates given fields of an existing task, field will ignore if it is null
     *
     * @param id the id of the task to save.
     * @param task the task to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated task,
     * or with status {@code 400 (Bad Request)} if the task is not valid,
     * or with status {@code 404 (Not Found)} if the task is not found,
     * or with status {@code 500 (Internal Server Error)} if the task couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Task> partialUpdateTask(@PathVariable(value = "id", required = false) final String id, @RequestBody Task task)
        throws URISyntaxException {
        log.debug("REST request to partial update Task partially : {}, {}", id, task);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        if (task.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, task.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Task existing = taskRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        task.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), task.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        task.setCreatedBy(existing.getCreatedBy());
        task.setCreatedDate(existing.getCreatedDate());
        task.setModifiedBy(AuditStamp.currentUser());
        task.setModifiedDate(AuditStamp.today());

        Optional<Task> result = taskRepository
            .findById(task.getId())
            .map(existingTask -> {
                if (task.getName() != null) {
                    existingTask.setName(task.getName());
                }
                if (task.getDescription() != null) {
                    existingTask.setDescription(task.getDescription());
                }
                if (task.getSchedule() != null) {
                    existingTask.setSchedule(task.getSchedule());
                }
                if (task.getScheduledAt() != null) {
                    existingTask.setScheduledAt(task.getScheduledAt());
                }
                if (task.getDuration() != null) {
                    existingTask.setDuration(task.getDuration());
                }
                if (task.getStatus() != null) {
                    existingTask.setStatus(task.getStatus());
                }
                if (task.getLocation() != null) {
                    existingTask.setLocation(task.getLocation());
                }
                if (task.getCaseId() != null) {
                    existingTask.setCaseId(task.getCaseId());
                }
                if (task.getAttendantId() != null) {
                    existingTask.setAttendantId(task.getAttendantId());
                }
                if (task.getTeamId() != null) {
                    existingTask.setTeamId(task.getTeamId());
                }
                if (task.getPatientId() != null) {
                    existingTask.setPatientId(task.getPatientId());
                }
                if (task.getAttendant() != null) {
                    existingTask.setAttendant(task.getAttendant());
                }
                if (task.getCreatedDate() != null) {
                    existingTask.setCreatedDate(task.getCreatedDate());
                }
                if (task.getModifiedDate() != null) {
                    existingTask.setModifiedDate(task.getModifiedDate());
                }
                if (task.getCreatedBy() != null) {
                    existingTask.setCreatedBy(task.getCreatedBy());
                }
                if (task.getModifiedBy() != null) {
                    existingTask.setModifiedBy(task.getModifiedBy());
                }

                return existingTask;
            })
            .map(taskRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, task.getId()));
    }

    /**
     * {@code GET  /tasks} : get all the tasks.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of tasks in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public ResponseEntity<List<Task>> getAllTasks(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of Tasks for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.CARE_PLAN);
        Page<Task> page = patientScope.findScopedPage(patientId, pageable, taskRepository::findAll, taskRepository::findByPatientId);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /tasks/:id} : get the "id" task.
     *
     * @param id the id of the task to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the task, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTask(@PathVariable("id") String id) {
        log.debug("REST request to get Task : {}", id);
        patientScope.requireRead(ClinicalDomain.CARE_PLAN);
        Optional<Task> task = taskRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(task);
    }

    /**
     * {@code DELETE  /tasks/:id} : delete the "id" task.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the task to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable("id") String id) {
        log.debug("REST request to delete Task : {}", id);
        if (taskRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/tasks/:id/archive} : retire a task from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — CARE_PLAN — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a task must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Task> archiveTask(@PathVariable("id") String id, @RequestBody(required = false) Map<String, String> body) {
        log.debug("REST request to archive Task : {}", id);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (taskRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Task archived = ArchiveSupport.archive(
            taskRepository.findById(id),
            id,
            professionalId(),
            reason.trim(),
            ENTITY_NAME,
            "task",
            taskRepository::save
        );
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/tasks/:id/unarchive} : put a task back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Task> unarchiveTask(@PathVariable("id") String id) {
        log.debug("REST request to unarchive Task : {}", id);
        patientScope.requireWrite(ClinicalDomain.CARE_PLAN);
        if (taskRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Task restored = ArchiveSupport.unarchive(taskRepository.findById(id), id, ENTITY_NAME, "task", taskRepository::save);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
