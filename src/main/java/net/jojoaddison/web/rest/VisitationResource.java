package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Visitation;
import net.jojoaddison.repository.VisitationRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.VisitationService;
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
 * REST controller for managing {@link net.jojoaddison.domain.Visitation}.
 */
@RestController
@RequestMapping("/api/visitations")
public class VisitationResource {

    private final Logger log = LoggerFactory.getLogger(VisitationResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceVisitation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final VisitationService visitationService;

    private final VisitationRepository visitationRepository;

    private final PatientScope patientScope;

    public VisitationResource(VisitationService visitationService, VisitationRepository visitationRepository, PatientScope patientScope) {
        this.visitationService = visitationService;
        this.visitationRepository = visitationRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /visitations} : Create a new visitation.
     *
     * @param visitation the visitation to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new visitation, or with status {@code 400 (Bad Request)} if the visitation has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Visitation> createVisitation(@RequestBody Visitation visitation) throws URISyntaxException {
        log.debug("REST request to save Visitation : {}", visitation);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (visitation.getId() != null) {
            throw new BadRequestAlertException("A new visitation cannot already have an ID", ENTITY_NAME, "idexists");
        }
        visitation.setPatientId(patientScope.requirePatientIdForWrite(visitation.getPatientId()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        visitation.setCreatedBy(AuditStamp.currentUser());
        visitation.setCreatedDate(AuditStamp.today());
        visitation.setModifiedBy(AuditStamp.currentUser());
        visitation.setModifiedDate(AuditStamp.today());
        Visitation result = visitationService.save(visitation);
        return ResponseEntity
            .created(new URI("/api/visitations/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /visitations/:id} : Updates an existing visitation.
     *
     * @param id the id of the visitation to save.
     * @param visitation the visitation to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated visitation,
     * or with status {@code 400 (Bad Request)} if the visitation is not valid,
     * or with status {@code 500 (Internal Server Error)} if the visitation couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Visitation> updateVisitation(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Visitation visitation
    ) throws URISyntaxException {
        log.debug("REST request to update Visitation : {}, {}", id, visitation);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (visitation.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, visitation.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Visitation existing = visitationRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        visitation.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), visitation.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        visitation.setCreatedBy(existing.getCreatedBy());
        visitation.setCreatedDate(existing.getCreatedDate());
        visitation.setModifiedBy(AuditStamp.currentUser());
        visitation.setModifiedDate(AuditStamp.today());

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a Visitation could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        visitation.setArchivedAt(existing.getArchivedAt());

        visitation.setArchivedById(existing.getArchivedById());

        visitation.setArchiveReason(existing.getArchiveReason());

        Visitation result = visitationService.update(visitation);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, visitation.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /visitations/:id} : Partial updates given fields of an existing visitation, field will ignore if it is null
     *
     * @param id the id of the visitation to save.
     * @param visitation the visitation to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated visitation,
     * or with status {@code 400 (Bad Request)} if the visitation is not valid,
     * or with status {@code 404 (Not Found)} if the visitation is not found,
     * or with status {@code 500 (Internal Server Error)} if the visitation couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Visitation> partialUpdateVisitation(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Visitation visitation
    ) throws URISyntaxException {
        log.debug("REST request to partial update Visitation partially : {}, {}", id, visitation);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (visitation.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, visitation.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Visitation existing = visitationRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        visitation.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), visitation.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        visitation.setCreatedBy(existing.getCreatedBy());
        visitation.setCreatedDate(existing.getCreatedDate());
        visitation.setModifiedBy(AuditStamp.currentUser());
        visitation.setModifiedDate(AuditStamp.today());

        Optional<Visitation> result = visitationService.partialUpdate(visitation);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, visitation.getId())
        );
    }

    /**
     * {@code GET  /visitations} : get all the visitations.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of visitations in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public ResponseEntity<List<Visitation>> getAllVisitations(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of Visitations for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.ENCOUNTER);
        Page<Visitation> page = patientScope.findScopedPage(
            patientId,
            pageable,
            visitationRepository::findAll,
            visitationRepository::findByPatientId
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /visitations/:id} : get the "id" visitation.
     *
     * @param id the id of the visitation to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the visitation, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Visitation> getVisitation(@PathVariable("id") String id) {
        log.debug("REST request to get Visitation : {}", id);
        patientScope.requireRead(ClinicalDomain.ENCOUNTER);
        Optional<Visitation> visitation = visitationService.findOne(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(visitation);
    }

    /**
     * {@code DELETE  /visitations/:id} : delete the "id" visitation.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the visitation to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVisitation(@PathVariable("id") String id) {
        log.debug("REST request to delete Visitation : {}", id);
        if (visitationRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        visitationService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/visitations/:id/archive} : retire a visitation from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — ENCOUNTER — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a visitation must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Visitation> archiveVisitation(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        log.debug("REST request to archive Visitation : {}", id);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (visitationRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Visitation archived = visitationService.archive(id, professionalId(), reason.trim());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/visitations/:id/unarchive} : put a visitation back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Visitation> unarchiveVisitation(@PathVariable("id") String id) {
        log.debug("REST request to unarchive Visitation : {}", id);
        patientScope.requireWrite(ClinicalDomain.ENCOUNTER);
        if (visitationRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Visitation restored = visitationService.unarchive(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
