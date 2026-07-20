package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.MedCase;
import net.jojoaddison.repository.MedCaseRepository;
import net.jojoaddison.service.MedCaseService;
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
 * REST controller for managing {@link net.jojoaddison.domain.MedCase}.
 */
@RestController
@RequestMapping("/api/med-cases")
public class MedCaseResource {

    private static final Logger LOG = LoggerFactory.getLogger(MedCaseResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceMedCase";

    @Value("${jhipster.clientApp.name:hcPatientService}")
    private String applicationName;

    private final MedCaseService medCaseService;

    private final MedCaseRepository medCaseRepository;

    public MedCaseResource(MedCaseService medCaseService, MedCaseRepository medCaseRepository) {
        this.medCaseService = medCaseService;
        this.medCaseRepository = medCaseRepository;
    }

    /**
     * {@code POST  /med-cases} : Create a new medCase.
     *
     * @param medCase the medCase to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new medCase, or with status {@code 400 (Bad Request)} if the medCase has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<MedCase> createMedCase(@RequestBody MedCase medCase) throws URISyntaxException {
        LOG.debug("REST request to save MedCase : {}", medCase);
        if (medCase.getId() != null) {
            throw new BadRequestAlertException("A new medCase cannot already have an ID", ENTITY_NAME, "idexists");
        }
        medCase = medCaseService.save(medCase);
        return ResponseEntity
            .created(new URI("/api/med-cases/" + medCase.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, medCase.getId()))
            .body(medCase);
    }

    /**
     * {@code PUT  /med-cases/:id} : Updates an existing medCase.
     *
     * @param id the id of the medCase to save.
     * @param medCase the medCase to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated medCase,
     * or with status {@code 400 (Bad Request)} if the medCase is not valid,
     * or with status {@code 500 (Internal Server Error)} if the medCase couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<MedCase> updateMedCase(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody MedCase medCase
    ) throws URISyntaxException {
        LOG.debug("REST request to update MedCase : {}, {}", id, medCase);
        if (medCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!medCaseRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        medCase = medCaseService.update(medCase);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, medCase.getId()))
            .body(medCase);
    }

    /**
     * {@code PATCH  /med-cases/:id} : Partial updates given fields of an existing medCase, field will ignore if it is null
     *
     * @param id the id of the medCase to save.
     * @param medCase the medCase to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated medCase,
     * or with status {@code 400 (Bad Request)} if the medCase is not valid,
     * or with status {@code 404 (Not Found)} if the medCase is not found,
     * or with status {@code 500 (Internal Server Error)} if the medCase couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<MedCase> partialUpdateMedCase(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody MedCase medCase
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update MedCase partially : {}, {}", id, medCase);
        if (medCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medCase.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!medCaseRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<MedCase> result = medCaseService.partialUpdate(medCase);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, medCase.getId())
        );
    }

    /**
     * {@code GET  /med-cases} : get all the Med Cases.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Med Cases in body.
     */
    @GetMapping("")
    public ResponseEntity<List<MedCase>> getAllMedCases(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of MedCases");
        Page<MedCase> page = medCaseService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /med-cases/:id} : get the "id" medCase.
     *
     * @param id the id of the medCase to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the medCase, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MedCase> getMedCase(@PathVariable("id") String id) {
        LOG.debug("REST request to get MedCase : {}", id);
        Optional<MedCase> medCase = medCaseService.findOne(id);
        return ResponseUtil.wrapOrNotFound(medCase);
    }

    /**
     * {@code DELETE  /med-cases/:id} : delete the "id" medCase.
     *
     * @param id the id of the medCase to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedCase(@PathVariable("id") String id) {
        LOG.debug("REST request to delete MedCase : {}", id);
        medCaseService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
