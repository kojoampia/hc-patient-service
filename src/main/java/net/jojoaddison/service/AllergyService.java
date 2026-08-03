package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.repository.AllergyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Allergy}.
 */
@Service
public class AllergyService {

    private final Logger log = LoggerFactory.getLogger(AllergyService.class);

    private final AllergyRepository allergyRepository;

    public AllergyService(AllergyRepository allergyRepository) {
        this.allergyRepository = allergyRepository;
    }

    /**
     * Save a allergy.
     *
     * @param allergy the entity to save.
     * @return the persisted entity.
     */
    public Allergy save(Allergy allergy) {
        log.debug("Request to save Allergy : {}", allergy);
        return allergyRepository.save(allergy);
    }

    /**
     * Update a allergy.
     *
     * @param allergy the entity to save.
     * @return the persisted entity.
     */
    public Allergy update(Allergy allergy) {
        log.debug("Request to update Allergy : {}", allergy);
        return allergyRepository.save(allergy);
    }

    /**
     * Partially update a allergy.
     *
     * @param allergy the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Allergy> partialUpdate(Allergy allergy) {
        log.debug("Request to partially update Allergy : {}", allergy);

        return allergyRepository
            .findById(allergy.getId())
            .map(existingAllergy -> {
                if (allergy.getPatientId() != null) {
                    existingAllergy.setPatientId(allergy.getPatientId());
                }
                if (allergy.getName() != null) {
                    existingAllergy.setName(allergy.getName());
                }
                if (allergy.getCategory() != null) {
                    existingAllergy.setCategory(allergy.getCategory());
                }
                if (allergy.getSeverity() != null) {
                    existingAllergy.setSeverity(allergy.getSeverity());
                }
                if (allergy.getReaction() != null) {
                    existingAllergy.setReaction(allergy.getReaction());
                }
                if (allergy.getNotedOn() != null) {
                    existingAllergy.setNotedOn(allergy.getNotedOn());
                }
                if (allergy.getNotedById() != null) {
                    existingAllergy.setNotedById(allergy.getNotedById());
                }
                if (allergy.getCreatedDate() != null) {
                    existingAllergy.setCreatedDate(allergy.getCreatedDate());
                }
                if (allergy.getModifiedDate() != null) {
                    existingAllergy.setModifiedDate(allergy.getModifiedDate());
                }
                if (allergy.getCreatedBy() != null) {
                    existingAllergy.setCreatedBy(allergy.getCreatedBy());
                }
                if (allergy.getModifiedBy() != null) {
                    existingAllergy.setModifiedBy(allergy.getModifiedBy());
                }

                return existingAllergy;
            })
            .map(allergyRepository::save);
    }

    /**
     * Get all the allergies.
     *
     * @return the list of entities.
     */
    public List<Allergy> findAll() {
        log.debug("Request to get all Allergies");
        return allergyRepository.findAll();
    }

    /**
     * Get one allergy by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Allergy> findOne(String id) {
        log.debug("Request to get Allergy : {}", id);
        return allergyRepository.findById(id);
    }

    /**
     * Delete the allergy by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Allergy : {}", id);
        allergyRepository.deleteById(id);
    }
}
