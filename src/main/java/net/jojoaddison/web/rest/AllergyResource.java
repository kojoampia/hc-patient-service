package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.AllergyService;
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
 * REST controller for managing {@link net.jojoaddison.domain.Allergy}.
 */
@RestController
@RequestMapping("/api/allergies")
public class AllergyResource {

    private final Logger log = LoggerFactory.getLogger(AllergyResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceAllergy";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AllergyService allergyService;

    private final AllergyRepository allergyRepository;

    private final PatientScope patientScope;

    public AllergyResource(AllergyService allergyService, AllergyRepository allergyRepository, PatientScope patientScope) {
        this.allergyService = allergyService;
        this.allergyRepository = allergyRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /allergies} : Create a new allergy.
     *
     * @param allergy the allergy to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new allergy, or with status {@code 400 (Bad Request)} if the allergy has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Allergy> createAllergy(@RequestBody Allergy allergy) throws URISyntaxException {
        log.debug("REST request to save Allergy : {}", allergy);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (allergy.getId() != null) {
            throw new BadRequestAlertException("A new allergy cannot already have an ID", ENTITY_NAME, "idexists");
        }
        allergy.setPatientId(patientScope.requirePatientIdForWrite(allergy.getPatientId()));
        // Provenance comes from the caller, never from the body — otherwise anyone could post a record
        // marked PROFESSIONAL and have it read as clinician-attested ever after.
        allergy.setSource(patientScope.currentActivitySource());
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        allergy.setCreatedBy(AuditStamp.currentUser());
        allergy.setCreatedDate(AuditStamp.today());
        allergy.setModifiedBy(AuditStamp.currentUser());
        allergy.setModifiedDate(AuditStamp.today());
        Allergy result = allergyService.save(allergy);
        return ResponseEntity
            .created(new URI("/api/allergies/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /allergies/:id} : Updates an existing allergy.
     *
     * @param id the id of the allergy to save.
     * @param allergy the allergy to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated allergy,
     * or with status {@code 400 (Bad Request)} if the allergy is not valid,
     * or with status {@code 500 (Internal Server Error)} if the allergy couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Allergy> updateAllergy(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Allergy allergy
    ) throws URISyntaxException {
        log.debug("REST request to update Allergy : {}, {}", id, allergy);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (allergy.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, allergy.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Allergy existing = allergyRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        allergy.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), allergy.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        allergy.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        allergy.setCreatedBy(existing.getCreatedBy());
        allergy.setCreatedDate(existing.getCreatedDate());
        allergy.setModifiedBy(AuditStamp.currentUser());
        allergy.setModifiedDate(AuditStamp.today());

        Allergy result = allergyService.update(allergy);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, allergy.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /allergies/:id} : Partial updates given fields of an existing allergy, field will ignore if it is null
     *
     * @param id the id of the allergy to save.
     * @param allergy the allergy to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated allergy,
     * or with status {@code 400 (Bad Request)} if the allergy is not valid,
     * or with status {@code 404 (Not Found)} if the allergy is not found,
     * or with status {@code 500 (Internal Server Error)} if the allergy couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Allergy> partialUpdateAllergy(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Allergy allergy
    ) throws URISyntaxException {
        log.debug("REST request to partial update Allergy partially : {}, {}", id, allergy);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (allergy.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, allergy.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Allergy existing = allergyRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        allergy.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), allergy.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        allergy.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        allergy.setCreatedBy(existing.getCreatedBy());
        allergy.setCreatedDate(existing.getCreatedDate());
        allergy.setModifiedBy(AuditStamp.currentUser());
        allergy.setModifiedDate(AuditStamp.today());

        Optional<Allergy> result = allergyService.partialUpdate(allergy);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, allergy.getId())
        );
    }

    /**
     * {@code GET  /allergies} : get all the allergies.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of allergies in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<Allergy> getAllAllergies(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all Allergys for patient {}", patientId);
        return patientScope.findScoped(patientId, allergyRepository::findAll, allergyRepository::findByPatientId);
    }

    /**
     * {@code GET  /allergies/:id} : get the "id" allergy.
     *
     * @param id the id of the allergy to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the allergy, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Allergy> getAllergy(@PathVariable("id") String id) {
        log.debug("REST request to get Allergy : {}", id);
        Optional<Allergy> allergy = allergyService.findOne(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(allergy);
    }

    /**
     * {@code DELETE  /allergies/:id} : delete the "id" allergy.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the allergy to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAllergy(@PathVariable("id") String id) {
        log.debug("REST request to delete Allergy : {}", id);
        if (allergyRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        allergyService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
