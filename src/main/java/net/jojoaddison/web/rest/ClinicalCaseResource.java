package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.ClinicalCaseService;
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
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        log.debug("REST request to get a page of ClinicalCases for patient {}", patientId);
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
     * @param id the id of the clinicalCase to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClinicalCase(@PathVariable("id") String id) {
        log.debug("REST request to delete ClinicalCase : {}", id);
        if (clinicalCaseRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        clinicalCaseService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
