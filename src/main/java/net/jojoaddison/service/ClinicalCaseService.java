package net.jojoaddison.service;

import java.time.Instant;
import java.util.Optional;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.repository.ClinicalCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.ClinicalCase}.
 */
@Service
public class ClinicalCaseService {

    private final Logger log = LoggerFactory.getLogger(ClinicalCaseService.class);

    /**
     * Matches the resource's own {@code ENTITY_NAME}, and is repeated rather than shared because the ArchUnit layer
     * rules do not let {@code service} read a constant out of {@code web}. It only reaches a client through the
     * error's {@code entityName}, so the two must agree or a caller sees a different entity named depending on which
     * layer refused them.
     */
    private static final String ENTITY_NAME = "hcPatientServiceClinicalCase";

    private final ClinicalCaseRepository clinicalCaseRepository;

    public ClinicalCaseService(ClinicalCaseRepository clinicalCaseRepository) {
        this.clinicalCaseRepository = clinicalCaseRepository;
    }

    /**
     * Save a clinicalCase.
     *
     * @param clinicalCase the entity to save.
     * @return the persisted entity.
     */
    public ClinicalCase save(ClinicalCase clinicalCase) {
        log.debug("Request to save ClinicalCase : {}", clinicalCase);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Update a clinicalCase.
     *
     * @param clinicalCase the entity to save.
     * @return the persisted entity.
     */
    public ClinicalCase update(ClinicalCase clinicalCase) {
        log.debug("Request to update ClinicalCase : {}", clinicalCase);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Partially update a clinicalCase.
     *
     * @param clinicalCase the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ClinicalCase> partialUpdate(ClinicalCase clinicalCase) {
        log.debug("Request to partially update ClinicalCase : {}", clinicalCase);

        return clinicalCaseRepository
            .findById(clinicalCase.getId())
            .map(existingClinicalCase -> {
                if (clinicalCase.getPatientId() != null) {
                    existingClinicalCase.setPatientId(clinicalCase.getPatientId());
                }
                if (clinicalCase.getCaseNumber() != null) {
                    existingClinicalCase.setCaseNumber(clinicalCase.getCaseNumber());
                }
                if (clinicalCase.getTitle() != null) {
                    existingClinicalCase.setTitle(clinicalCase.getTitle());
                }
                if (clinicalCase.getOpenedAt() != null) {
                    existingClinicalCase.setOpenedAt(clinicalCase.getOpenedAt());
                }
                if (clinicalCase.getClosedAt() != null) {
                    existingClinicalCase.setClosedAt(clinicalCase.getClosedAt());
                }
                if (clinicalCase.getBrief() != null) {
                    existingClinicalCase.setBrief(clinicalCase.getBrief());
                }
                if (clinicalCase.getStatus() != null) {
                    existingClinicalCase.setStatus(clinicalCase.getStatus());
                }
                if (clinicalCase.getSymptoms() != null) {
                    existingClinicalCase.setSymptoms(clinicalCase.getSymptoms());
                }
                if (clinicalCase.getDiagnosis() != null) {
                    existingClinicalCase.setDiagnosis(clinicalCase.getDiagnosis());
                }
                if (clinicalCase.getAssignedProfessionalId() != null) {
                    existingClinicalCase.setAssignedProfessionalId(clinicalCase.getAssignedProfessionalId());
                }
                if (clinicalCase.getAssignedRosterId() != null) {
                    existingClinicalCase.setAssignedRosterId(clinicalCase.getAssignedRosterId());
                }
                // archivedAt, archivedById and archiveReason are deliberately absent from this merge, and must stay
                // absent. They are set by /archive and /unarchive, which are ROLE_PROFESSIONAL and stamp the caller;
                // merging them here would let anyone who may edit a case archive it by sending a field, and let them
                // choose whose name was on it. This is the same rule that keeps CareDelegation off a generic PATCH.

                return existingClinicalCase;
            })
            .map(clinicalCaseRepository::save);
    }

    /**
     * Get all the clinicalCases.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<ClinicalCase> findAll(Pageable pageable) {
        log.debug("Request to get all ClinicalCases");
        return clinicalCaseRepository.findAll(pageable);
    }

    /**
     * Get all the clinicalCases with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ClinicalCase> findAllWithEagerRelationships(Pageable pageable) {
        return clinicalCaseRepository.findAllWithEagerRelationships(pageable);
    }

    /**
     * The same, minus anything archived.
     *
     * @param pageable the pagination information.
     * @return every live case.
     */
    public Page<ClinicalCase> findAllLiveWithEagerRelationships(Pageable pageable) {
        return clinicalCaseRepository.findAllLiveWithEagerRelationships(pageable);
    }

    /**
     * Retires a case from the working queue.
     *
     * <p>Archiving exists because patient data is never deleted, and a queue that can only grow is not a queue. It is
     * the professional's replacement for the delete they do not have: the case keeps every field it had, keeps its
     * place in the patient's record, and stops appearing in the lists clinicians work from.</p>
     *
     * <p>Idempotent by refusal rather than by silence. Archiving something already archived is a mistake somewhere —
     * two clinicians on the same queue, or a double-submitted button — and answering 200 to it would overwrite the
     * first archiver's name and reason with the second's, quietly rewriting who retired the case and why.</p>
     *
     * @param id the case to archive.
     * @param professionalId the caller, as resolved by the resource; stamped rather than accepted from a payload.
     * @param reason why it is being retired. Required: an archive with no reason is the delete this replaces.
     * @return the archived case.
     * @throws DomainStateException if it is archived already.
     */
    public ClinicalCase archive(String id, String professionalId, String reason) {
        log.debug("Request to archive ClinicalCase : {}", id);
        ClinicalCase clinicalCase = clinicalCaseRepository
            .findById(id)
            .orElseThrow(() -> new DomainStateException("No such clinical case", ENTITY_NAME, "idnotfound"));
        if (clinicalCase.isArchived()) {
            throw new DomainStateException(
                "This case was already archived on " + clinicalCase.getArchivedAt(),
                ENTITY_NAME,
                "alreadyarchived"
            );
        }
        clinicalCase.setArchivedAt(Instant.now());
        clinicalCase.setArchivedById(professionalId);
        clinicalCase.setArchiveReason(reason);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Returns an archived case to the working queue.
     *
     * <p>Not an afterthought. Without it archiving is a delete with extra steps — the one operation a clinician can
     * perform on a patient's record that nobody can undo — and the mistake it invites is archiving the wrong row of a
     * list. Clearing all three fields rather than leaving them as history is deliberate: a case is either live or it
     * is archived, and a live case carrying a stale archiver and reason would read as though it were both.</p>
     *
     * @param id the case to restore.
     * @return the restored case.
     * @throws DomainStateException if it was not archived to begin with.
     */
    public ClinicalCase unarchive(String id) {
        log.debug("Request to unarchive ClinicalCase : {}", id);
        ClinicalCase clinicalCase = clinicalCaseRepository
            .findById(id)
            .orElseThrow(() -> new DomainStateException("No such clinical case", ENTITY_NAME, "idnotfound"));
        if (!clinicalCase.isArchived()) {
            throw new DomainStateException("This case is not archived", ENTITY_NAME, "notarchived");
        }
        clinicalCase.setArchivedAt(null);
        clinicalCase.setArchivedById(null);
        clinicalCase.setArchiveReason(null);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Get one clinicalCase by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<ClinicalCase> findOne(String id) {
        log.debug("Request to get ClinicalCase : {}", id);
        return clinicalCaseRepository.findOneWithEagerRelationships(id);
    }

    /**
     * Delete the clinicalCase by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete ClinicalCase : {}", id);
        clinicalCaseRepository.deleteById(id);
    }
}
