package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ClinicalCaseService;
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
 * REST controller for managing {@link net.jojoaddison.domain.ClinicalCase}.
 */
@RestController
@RequestMapping("/api/clinical-cases")
public class ClinicalCaseResource {

    private final Logger log = LoggerFactory.getLogger(ClinicalCaseResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceClinicalCase";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ClinicalCaseService clinicalCaseService;

    private final ClinicalCaseRepository clinicalCaseRepository;

    private final PatientScope patientScope;

    public ClinicalCaseResource(
        ClinicalCaseService clinicalCaseService,
        ClinicalCaseRepository clinicalCaseRepository,
        PatientScope patientScope
    ) {
        this.clinicalCaseService = clinicalCaseService;
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /clinical-cases} : Create a new clinicalCase.
     *
     * @param clinicalCase the clinicalCase to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new clinicalCase, or with status {@code 400 (Bad Request)} if the clinicalCase has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ClinicalCase> createClinicalCase(@RequestBody ClinicalCase clinicalCase) throws URISyntaxException {
        log.debug("REST request to save ClinicalCase : {}", clinicalCase);
        if (clinicalCase.getId() != null) {
            throw new BadRequestAlertException("A new clinicalCase cannot already have an ID", ENTITY_NAME, "idexists");
        }
        clinicalCase.setPatientId(patientScope.requirePatientIdForWrite(clinicalCase.getPatientId()));
        ClinicalCase result = clinicalCaseService.save(clinicalCase);
        return ResponseEntity
            .created(new URI("/api/clinical-cases/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /clinical-cases/:id} : Updates an existing clinicalCase.
     *
     * @param id the id of the clinicalCase to save.
     * @param clinicalCase the clinicalCase to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated clinicalCase,
     * or with status {@code 400 (Bad Request)} if the clinicalCase is not valid,
     * or with status {@code 500 (Internal Server Error)} if the clinicalCase couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClinicalCase> updateClinicalCase(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ClinicalCase clinicalCase
    ) throws URISyntaxException {
        log.debug("REST request to update ClinicalCase : {}, {}", id, clinicalCase);
        if (clinicalCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, clinicalCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        ClinicalCase existing = clinicalCaseRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        clinicalCase.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), clinicalCase.getPatientId()));

        ClinicalCase result = clinicalCaseService.update(clinicalCase);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, clinicalCase.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /clinical-cases/:id} : Partial updates given fields of an existing clinicalCase, field will ignore if it is null
     *
     * @param id the id of the clinicalCase to save.
     * @param clinicalCase the clinicalCase to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated clinicalCase,
     * or with status {@code 400 (Bad Request)} if the clinicalCase is not valid,
     * or with status {@code 404 (Not Found)} if the clinicalCase is not found,
     * or with status {@code 500 (Internal Server Error)} if the clinicalCase couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ClinicalCase> partialUpdateClinicalCase(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody ClinicalCase clinicalCase
    ) throws URISyntaxException {
        log.debug("REST request to partial update ClinicalCase partially : {}, {}", id, clinicalCase);
        if (clinicalCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, clinicalCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        ClinicalCase existing = clinicalCaseRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        clinicalCase.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), clinicalCase.getPatientId()));

        Optional<ClinicalCase> result = clinicalCaseService.partialUpdate(clinicalCase);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, clinicalCase.getId())
        );
    }

    /**
     * {@code GET  /clinical-cases} : get all the clinicalCases.
     *
     * @param pageable the pagination information.
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of clinicalCases in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ClinicalCase>> getAllClinicalCases(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload,
        @RequestParam(name = "includeArchived", required = false, defaultValue = "false") boolean includeArchived
    ) {
        log.debug("REST request to get a page of ClinicalCases for patient {}", patientId);
        // Archived cases are excluded unless asked for. This is the half of archiving that clients actually see: the
        // point of retiring a case is that the working queue stops showing it, and a default of "everything" would
        // leave every caller to remember to filter — which is what the dashboard was doing in a client-side Set.
        //
        // They are excluded from the list, not hidden: GET /{id} still returns an archived case, so a link or a
        // bookmark to one keeps working and nothing has to be un-archived merely to be read.
        if (!includeArchived) {
            Page<ClinicalCase> live = patientScope.findScopedPage(
                patientId,
                pageable,
                requested -> clinicalCaseService.findAllLiveWithEagerRelationships(requested),
                clinicalCaseRepository::findByPatientIdAndArchivedAtIsNull
            );
            HttpHeaders liveHeaders = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), live);
            return ResponseEntity.ok().headers(liveHeaders).body(live.getContent());
        }
        // The unscoped branch reaches the eager-loading query, so it is passed as the "findAll" arm and only an
        // unrestricted caller can ever get there. A patient always lands on findByPatientId — Recommendations are a
        // DBRef and a patient-scoped query cannot eager-load them the way findAllWithEagerRelationships does; the
        // case list does not render them anyway.
        Page<ClinicalCase> page = patientScope.findScopedPage(
            patientId,
            pageable,
            requested -> eagerload ? clinicalCaseService.findAllWithEagerRelationships(requested) : clinicalCaseService.findAll(requested),
            clinicalCaseRepository::findByPatientId
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /clinical-cases/:id} : get the "id" clinicalCase.
     *
     * @param id the id of the clinicalCase to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the clinicalCase, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClinicalCase> getClinicalCase(@PathVariable("id") String id) {
        log.debug("REST request to get ClinicalCase : {}", id);
        Optional<ClinicalCase> clinicalCase = clinicalCaseService
            .findOne(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(clinicalCase);
    }

    /**
     * {@code DELETE  /clinical-cases/:id} : delete the "id" clinicalCase.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the clinicalCase to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClinicalCase(@PathVariable("id") String id) {
        log.debug("REST request to delete ClinicalCase : {}", id);
        if (clinicalCaseRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        clinicalCaseService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/clinical-cases/:id/archive} : retire a case from the working queue.
     *
     * <p>The professional-only replacement for the delete that patient data does not allow. The case keeps every
     * field it had and its place in the patient's record; it stops appearing in the lists clinicians work from.</p>
     *
     * <p><strong>{@code ROLE_PROFESSIONAL} only</strong>, following {@code CareDelegation}'s activate and
     * countersign rather than the DELETE above. Retiring a clinical episode is a clinical judgement about whether a
     * patient is still being treated for something; {@code ROLE_ADMIN} is an operational role, and it already holds
     * the harder power here.</p>
     *
     * @param id the case to archive.
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     * @return the archived case.
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<ClinicalCase> archiveClinicalCase(
        @PathVariable("id") String id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        log.debug("REST request to archive ClinicalCase : {}", id);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility is checked before existence is admitted, exactly as the read endpoints do: a caller who may not
        // see a case must not be able to learn that it exists by archiving it.
        if (clinicalCaseRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ClinicalCase archived = clinicalCaseService.archive(id, professionalId(), reason.trim());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/clinical-cases/:id/unarchive} : put a case back in the working queue.
     *
     * <p>The way back, and not optional. Without it archiving is a delete with extra steps — the one thing a
     * clinician could do to a record that nobody could undo — and the mistake it invites is archiving the wrong row
     * of a list.</p>
     *
     * @param id the case to restore.
     * @return the restored case.
     */
    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<ClinicalCase> unarchiveClinicalCase(@PathVariable("id") String id) {
        log.debug("REST request to unarchive ClinicalCase : {}", id);
        if (clinicalCaseRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ClinicalCase restored = clinicalCaseService.unarchive(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /**
     * Who archived it.
     *
     * <p>The login rather than a {@code Professional} document id, for the reason {@code CareDelegationResource}
     * gives about signatures: this service has no user management and no reliable mapping from a token to a staff
     * record. If that mapping ever exists, both places change together.</p>
     */
    private String professionalId() {
        return SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() ->
                new BadRequestAlertException("The archiving professional could not be identified", ENTITY_NAME, "noarchivist")
            );
    }
}
