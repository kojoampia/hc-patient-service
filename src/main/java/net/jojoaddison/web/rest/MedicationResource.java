package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
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
 * REST controller for managing {@link net.jojoaddison.domain.Medication}.
 */
@RestController
@RequestMapping("/api/medications")
public class MedicationResource {

    private final Logger log = LoggerFactory.getLogger(MedicationResource.class);

    private static final String ENTITY_NAME = "patientMsMedication";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MedicationRepository medicationRepository;

    private final PatientScope patientScope;

    public MedicationResource(MedicationRepository medicationRepository, PatientScope patientScope) {
        this.medicationRepository = medicationRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /medications} : Create a new medication.
     *
     * @param medication the medication to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new medication, or with status {@code 400 (Bad Request)} if the medication has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Medication> createMedication(@RequestBody Medication medication) throws URISyntaxException {
        log.debug("REST request to save Medication : {}", medication);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (medication.getId() != null) {
            throw new BadRequestAlertException("A new medication cannot already have an ID", ENTITY_NAME, "idexists");
        }
        medication.setPatientId(patientScope.requirePatientIdForWrite(medication.getPatientId()));
        // Provenance comes from the caller, never from the body — otherwise anyone could post a record
        // marked PROFESSIONAL and have it read as clinician-attested ever after.
        medication.setSource(patientScope.currentActivitySource());
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        medication.setCreatedBy(AuditStamp.currentUser());
        medication.setCreatedDate(AuditStamp.today());
        medication.setModifiedBy(AuditStamp.currentUser());
        medication.setModifiedDate(AuditStamp.today());
        Medication result = medicationRepository.save(medication);
        return ResponseEntity
            .created(new URI("/api/medications/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /medications/:id} : Updates an existing medication.
     *
     * @param id the id of the medication to save.
     * @param medication the medication to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated medication,
     * or with status {@code 400 (Bad Request)} if the medication is not valid,
     * or with status {@code 500 (Internal Server Error)} if the medication couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Medication> updateMedication(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Medication medication
    ) throws URISyntaxException {
        log.debug("REST request to update Medication : {}, {}", id, medication);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (medication.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medication.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Medication existing = medicationRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        medication.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), medication.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        medication.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        medication.setCreatedBy(existing.getCreatedBy());
        medication.setCreatedDate(existing.getCreatedDate());
        medication.setModifiedBy(AuditStamp.currentUser());
        medication.setModifiedDate(AuditStamp.today());

        Medication result = medicationRepository.save(medication);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, medication.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /medications/:id} : Partial updates given fields of an existing medication, field will ignore if it is null
     *
     * @param id the id of the medication to save.
     * @param medication the medication to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated medication,
     * or with status {@code 400 (Bad Request)} if the medication is not valid,
     * or with status {@code 404 (Not Found)} if the medication is not found,
     * or with status {@code 500 (Internal Server Error)} if the medication couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Medication> partialUpdateMedication(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Medication medication
    ) throws URISyntaxException {
        log.debug("REST request to partial update Medication partially : {}, {}", id, medication);
        patientScope.requireWrite(ClinicalDomain.MEDICATION);
        if (medication.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medication.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Medication existing = medicationRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        medication.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), medication.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        medication.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        medication.setCreatedBy(existing.getCreatedBy());
        medication.setCreatedDate(existing.getCreatedDate());
        medication.setModifiedBy(AuditStamp.currentUser());
        medication.setModifiedDate(AuditStamp.today());

        Optional<Medication> result = medicationRepository
            .findById(medication.getId())
            .map(existingMedication -> {
                if (medication.getName() != null) {
                    existingMedication.setName(medication.getName());
                }
                if (medication.getDescription() != null) {
                    existingMedication.setDescription(medication.getDescription());
                }
                if (medication.getPatientId() != null) {
                    existingMedication.setPatientId(medication.getPatientId());
                }
                if (medication.getCaseId() != null) {
                    existingMedication.setCaseId(medication.getCaseId());
                }
                if (medication.getPrescription() != null) {
                    existingMedication.setPrescription(medication.getPrescription());
                }
                if (medication.getDosage() != null) {
                    existingMedication.setDosage(medication.getDosage());
                }
                if (medication.getStatus() != null) {
                    existingMedication.setStatus(medication.getStatus());
                }
                if (medication.getStartedOn() != null) {
                    existingMedication.setStartedOn(medication.getStartedOn());
                }
                if (medication.getPrescribedById() != null) {
                    existingMedication.setPrescribedById(medication.getPrescribedById());
                }
                if (medication.getCreatedDate() != null) {
                    existingMedication.setCreatedDate(medication.getCreatedDate());
                }
                if (medication.getModifiedDate() != null) {
                    existingMedication.setModifiedDate(medication.getModifiedDate());
                }
                if (medication.getCreatedBy() != null) {
                    existingMedication.setCreatedBy(medication.getCreatedBy());
                }
                if (medication.getModifiedBy() != null) {
                    existingMedication.setModifiedBy(medication.getModifiedBy());
                }

                return existingMedication;
            })
            .map(medicationRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, medication.getId())
        );
    }

    /**
     * {@code GET  /medications} : get all the medications.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of medications in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public ResponseEntity<List<Medication>> getAllMedications(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of Medications for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.MEDICATION);
        Page<Medication> page = patientScope.findScopedPage(
            patientId,
            pageable,
            medicationRepository::findAll,
            medicationRepository::findByPatientId
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /medications/:id} : get the "id" medication.
     *
     * @param id the id of the medication to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the medication, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Medication> getMedication(@PathVariable("id") String id) {
        log.debug("REST request to get Medication : {}", id);
        patientScope.requireRead(ClinicalDomain.MEDICATION);
        Optional<Medication> medication = medicationRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(medication);
    }

    /**
     * {@code DELETE  /medications/:id} : delete the "id" medication.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the medication to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedication(@PathVariable("id") String id) {
        log.debug("REST request to delete Medication : {}", id);
        if (medicationRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        medicationRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
