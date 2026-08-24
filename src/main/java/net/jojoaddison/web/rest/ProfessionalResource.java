package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ProfessionalService;
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
 * REST controller for managing {@link net.jojoaddison.domain.Professional}.
 */
@RestController
@RequestMapping("/api/professionals")
public class ProfessionalResource {

    private final Logger log = LoggerFactory.getLogger(ProfessionalResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceProfessional";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProfessionalService professionalService;

    private final ProfessionalRepository professionalRepository;

    public ProfessionalResource(ProfessionalService professionalService, ProfessionalRepository professionalRepository) {
        this.professionalService = professionalService;
        this.professionalRepository = professionalRepository;
    }

    /**
     * {@code POST  /professionals} : Create a new professional.
     *
     * @param professional the professional to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new professional, or with status {@code 400 (Bad Request)} if the professional has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    // Reference data: readable by any authenticated caller, writable only by staff. Before 2026-08-05 any
    // patient could rewrite the clinical staff directory, retitle a clinical recommendation or delete a
    // care team outright, because the only rule anywhere was "is authenticated".
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Professional> createProfessional(@RequestBody Professional professional) throws URISyntaxException {
        log.debug("REST request to save Professional : {}", professional);
        if (professional.getId() != null) {
            throw new BadRequestAlertException("A new professional cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Professional result = professionalService.save(professional);
        return ResponseEntity
            .created(new URI("/api/professionals/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /professionals/:id} : Updates an existing professional.
     *
     * @param id the id of the professional to save.
     * @param professional the professional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated professional,
     * or with status {@code 400 (Bad Request)} if the professional is not valid,
     * or with status {@code 500 (Internal Server Error)} if the professional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Professional> updateProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Professional professional
    ) throws URISyntaxException {
        log.debug("REST request to update Professional : {}, {}", id, professional);
        if (professional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, professional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!professionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Professional result = professionalService.update(professional);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, professional.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /professionals/:id} : Partial updates given fields of an existing professional, field will ignore if it is null
     *
     * @param id the id of the professional to save.
     * @param professional the professional to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated professional,
     * or with status {@code 400 (Bad Request)} if the professional is not valid,
     * or with status {@code 404 (Not Found)} if the professional is not found,
     * or with status {@code 500 (Internal Server Error)} if the professional couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Professional> partialUpdateProfessional(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Professional professional
    ) throws URISyntaxException {
        log.debug("REST request to partial update Professional partially : {}, {}", id, professional);
        if (professional.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, professional.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!professionalRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Professional> result = professionalService.partialUpdate(professional);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, professional.getId())
        );
    }

    /**
     * {@code GET  /professionals} : get all the professionals.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of professionals in body.
     */
    @GetMapping("")
    public List<Professional> getAllProfessionals() {
        log.debug("REST request to get all Professionals");
        return professionalService.findAll().stream().map(this::redactForNonStaff).toList();
    }

    /**
     * {@code GET  /professionals/:id} : get the "id" professional.
     *
     * @param id the id of the professional to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the professional, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Professional> getProfessional(@PathVariable("id") String id) {
        log.debug("REST request to get Professional : {}", id);
        Optional<Professional> professional = professionalService.findOne(id);
        return ResponseUtil.wrapOrNotFound(professional.map(this::redactForNonStaff));
    }

    /**
     * {@code DELETE  /professionals/:id} : delete the "id" professional.
     *
     * @param id the id of the professional to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Void> deleteProfessional(@PathVariable("id") String id) {
        log.debug("REST request to delete Professional : {}", id);
        professionalService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * Removes a clinician's direct contact details for callers who are not staff.
     *
     * <p>The patient dashboard's care-team panel renders name, role, initials and location — it never reads
     * {@code email} or {@code phoneNumber}. Serving them anyway turned this endpoint into a staff directory
     * complete with direct lines, available to anyone who registered, and registration is open to the internet.</p>
     *
     * <p>Redacting on the way out rather than with a projection query keeps one source of truth for the document and
     * means a new field is visible by default and has to be considered here — the opposite failure mode to a
     * projection that silently stops returning something.</p>
     *
     * <p>The instance is copied first. Mutating what the repository returned would be fine for a Mongo document
     * today, but it is the kind of thing that becomes a cache-poisoning bug the moment anything caches.</p>
     */
    private Professional redactForNonStaff(Professional professional) {
        // Staff is an administrator or any clinical discipline. Read from AuthoritiesConstants.CLINICAL rather than
        // named one by one, so a ninth discipline does not quietly start seeing colleagues' direct lines redacted.
        Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();
        if (authorities.contains(AuthoritiesConstants.ADMIN) || !Collections.disjoint(authorities, AuthoritiesConstants.CLINICAL)) {
            return professional;
        }
        Professional redacted = new Professional();
        redacted.setId(professional.getId());
        redacted.setFirstName(professional.getFirstName());
        redacted.setLastName(professional.getLastName());
        // How a person is addressed is part of their name, not a contact detail: a patient reading their own record
        // sees "Dr. Grace Mensah" for the same reason they see "Grace Mensah".
        redacted.setHonorific(professional.getHonorific());
        redacted.setRole(professional.getRole());
        redacted.setSpecialty(professional.getSpecialty());
        redacted.setImageUrl(professional.getImageUrl());
        redacted.setInitials(professional.getInitials());
        redacted.setLocation(professional.getLocation());
        redacted.setTeamId(professional.getTeamId());
        // email and phoneNumber deliberately omitted.
        return redacted;
    }
}
