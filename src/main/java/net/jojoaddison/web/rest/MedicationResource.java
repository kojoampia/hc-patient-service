package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;
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

    public MedicationResource(MedicationRepository medicationRepository) {
        this.medicationRepository = medicationRepository;
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
        if (medication.getId() != null) {
            throw new BadRequestAlertException("A new medication cannot already have an ID", ENTITY_NAME, "idexists");
        }
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
        if (medication.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medication.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!medicationRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

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
        if (medication.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, medication.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!medicationRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

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
                if (medication.getPrescription() != null) {
                    existingMedication.setPrescription(medication.getPrescription());
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
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of medications in body.
     */
    @GetMapping("")
    public List<Medication> getAllMedications() {
        log.debug("REST request to get all Medications");
        return medicationRepository.findAll();
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
        Optional<Medication> medication = medicationRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(medication);
    }

    /**
     * {@code DELETE  /medications/:id} : delete the "id" medication.
     *
     * @param id the id of the medication to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedication(@PathVariable("id") String id) {
        log.debug("REST request to delete Medication : {}", id);
        medicationRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
