package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.repository.ClinicalCaseRepository;
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

    private static final Logger LOG = LoggerFactory.getLogger(ClinicalCaseResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceClinicalCase";

    @Value("${jhipster.clientApp.name:hcPatientService}")
    private String applicationName;

    private final ClinicalCaseService clinicalCaseService;

    private final ClinicalCaseRepository clinicalCaseRepository;

    public ClinicalCaseResource(ClinicalCaseService clinicalCaseService, ClinicalCaseRepository clinicalCaseRepository) {
        this.clinicalCaseService = clinicalCaseService;
        this.clinicalCaseRepository = clinicalCaseRepository;
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
        LOG.debug("REST request to save ClinicalCase : {}", clinicalCase);
        if (clinicalCase.getId() != null) {
            throw new BadRequestAlertException("A new clinicalCase cannot already have an ID", ENTITY_NAME, "idexists");
        }
        clinicalCase = clinicalCaseService.save(clinicalCase);
        return ResponseEntity
            .created(new URI("/api/clinical-cases/" + clinicalCase.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, clinicalCase.getId()))
            .body(clinicalCase);
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
        LOG.debug("REST request to update ClinicalCase : {}, {}", id, clinicalCase);
        if (clinicalCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, clinicalCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!clinicalCaseRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        clinicalCase = clinicalCaseService.update(clinicalCase);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, clinicalCase.getId()))
            .body(clinicalCase);
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
        LOG.debug("REST request to partial update ClinicalCase partially : {}, {}", id, clinicalCase);
        if (clinicalCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, clinicalCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!clinicalCaseRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ClinicalCase> result = clinicalCaseService.partialUpdate(clinicalCase);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, clinicalCase.getId())
        );
    }

    /**
     * {@code GET  /clinical-cases} : get all the Med Cases.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Med Cases in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ClinicalCase>> getAllClinicalCases(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of ClinicalCases");
        Page<ClinicalCase> page = clinicalCaseService.findAll(pageable);
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
        LOG.debug("REST request to get ClinicalCase : {}", id);
        Optional<ClinicalCase> clinicalCase = clinicalCaseService.findOne(id);
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
        LOG.debug("REST request to delete ClinicalCase : {}", id);
        clinicalCaseService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
