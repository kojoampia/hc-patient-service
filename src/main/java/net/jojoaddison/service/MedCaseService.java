package net.jojoaddison.service;

import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.MedCase;
import net.jojoaddison.repository.MedCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.MedCase}.
 */
@Service
public class MedCaseService {

    private static final Logger LOG = LoggerFactory.getLogger(MedCaseService.class);

    private final MedCaseRepository medCaseRepository;

    public MedCaseService(MedCaseRepository medCaseRepository) {
        this.medCaseRepository = medCaseRepository;
    }

    /**
     * Save a medCase.
     *
     * @param medCase the entity to save.
     * @return the persisted entity.
     */
    public MedCase save(MedCase medCase) {
        LOG.debug("Request to save MedCase : {}", medCase);
        return medCaseRepository.save(medCase);
    }

    /**
     * Update a medCase.
     *
     * @param medCase the entity to save.
     * @return the persisted entity.
     */
    public MedCase update(MedCase medCase) {
        LOG.debug("Request to update MedCase : {}", medCase);
        return medCaseRepository.save(medCase);
    }

    /**
     * Partially update a medCase.
     *
     * @param medCase the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<MedCase> partialUpdate(MedCase medCase) {
        LOG.debug("Request to partially update MedCase : {}", medCase);

        return medCaseRepository
            .findById(medCase.getId())
            .map(existingMedCase -> {
                updateIfPresent(existingMedCase::setSymptoms, medCase.getSymptoms());
                updateIfPresent(existingMedCase::setDiagnoses, medCase.getDiagnoses());
                updateIfPresent(existingMedCase::setRecommendations, medCase.getRecommendations());
                updateIfPresent(existingMedCase::setCreatedDate, medCase.getCreatedDate());
                updateIfPresent(existingMedCase::setCreatedBy, medCase.getCreatedBy());
                updateIfPresent(existingMedCase::setModifiedDate, medCase.getModifiedDate());
                updateIfPresent(existingMedCase::setModifiedBy, medCase.getModifiedBy());

                return existingMedCase;
            })
            .map(medCaseRepository::save);
    }

    /**
     * Get all the medCases.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<MedCase> findAll(Pageable pageable) {
        LOG.debug("Request to get all MedCases");
        return medCaseRepository.findAll(pageable);
    }

    /**
     * Get one medCase by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<MedCase> findOne(String id) {
        LOG.debug("Request to get MedCase : {}", id);
        return medCaseRepository.findById(id);
    }

    /**
     * Delete the medCase by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete MedCase : {}", id);
        medCaseRepository.deleteById(id);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
