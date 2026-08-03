package net.jojoaddison.service;

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
