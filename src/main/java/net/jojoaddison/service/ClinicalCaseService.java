package net.jojoaddison.service;

import java.util.Optional;
import java.util.function.Consumer;
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

    private static final Logger LOG = LoggerFactory.getLogger(ClinicalCaseService.class);

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
        LOG.debug("Request to save ClinicalCase : {}", clinicalCase);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Update a clinicalCase.
     *
     * @param clinicalCase the entity to save.
     * @return the persisted entity.
     */
    public ClinicalCase update(ClinicalCase clinicalCase) {
        LOG.debug("Request to update ClinicalCase : {}", clinicalCase);
        return clinicalCaseRepository.save(clinicalCase);
    }

    /**
     * Partially update a clinicalCase.
     *
     * @param clinicalCase the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ClinicalCase> partialUpdate(ClinicalCase clinicalCase) {
        LOG.debug("Request to partially update ClinicalCase : {}", clinicalCase);

        return clinicalCaseRepository
            .findById(clinicalCase.getId())
            .map(existingClinicalCase -> {
                updateIfPresent(existingClinicalCase::setPatientId, clinicalCase.getPatientId());
                updateIfPresent(existingClinicalCase::setOpenedAt, clinicalCase.getOpenedAt());
                updateIfPresent(existingClinicalCase::setBrief, clinicalCase.getBrief());
                updateIfPresent(existingClinicalCase::setStatus, clinicalCase.getStatus());
                updateIfPresent(existingClinicalCase::setSymptoms, clinicalCase.getSymptoms());
                updateIfPresent(existingClinicalCase::setDiagnosis, clinicalCase.getDiagnosis());
                updateIfPresent(existingClinicalCase::setAssignedProfessionalId, clinicalCase.getAssignedProfessionalId());
                updateIfPresent(existingClinicalCase::setAssignedRosterId, clinicalCase.getAssignedRosterId());
                // `recommendations` is deliberately not merged here: it is a relationship, and an empty set on the
                // incoming patch is indistinguishable from "not supplied". Use PUT, or a dedicated endpoint, to
                // change which recommendations a case carries.

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
        LOG.debug("Request to get all ClinicalCases");
        return clinicalCaseRepository.findAll(pageable);
    }

    /**
     * Get one clinicalCase by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<ClinicalCase> findOne(String id) {
        LOG.debug("Request to get ClinicalCase : {}", id);
        return clinicalCaseRepository.findById(id);
    }

    /**
     * Delete the clinicalCase by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete ClinicalCase : {}", id);
        clinicalCaseRepository.deleteById(id);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
