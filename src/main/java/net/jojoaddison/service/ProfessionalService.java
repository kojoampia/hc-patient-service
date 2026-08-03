package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Professional}.
 */
@Service
public class ProfessionalService {

    private final Logger log = LoggerFactory.getLogger(ProfessionalService.class);

    private final ProfessionalRepository professionalRepository;

    public ProfessionalService(ProfessionalRepository professionalRepository) {
        this.professionalRepository = professionalRepository;
    }

    /**
     * Save a professional.
     *
     * @param professional the entity to save.
     * @return the persisted entity.
     */
    public Professional save(Professional professional) {
        log.debug("Request to save Professional : {}", professional);
        return professionalRepository.save(professional);
    }

    /**
     * Update a professional.
     *
     * @param professional the entity to save.
     * @return the persisted entity.
     */
    public Professional update(Professional professional) {
        log.debug("Request to update Professional : {}", professional);
        return professionalRepository.save(professional);
    }

    /**
     * Partially update a professional.
     *
     * @param professional the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Professional> partialUpdate(Professional professional) {
        log.debug("Request to partially update Professional : {}", professional);

        return professionalRepository
            .findById(professional.getId())
            .map(existingProfessional -> {
                if (professional.getFirstName() != null) {
                    existingProfessional.setFirstName(professional.getFirstName());
                }
                if (professional.getLastName() != null) {
                    existingProfessional.setLastName(professional.getLastName());
                }
                if (professional.getRole() != null) {
                    existingProfessional.setRole(professional.getRole());
                }
                if (professional.getSpecialty() != null) {
                    existingProfessional.setSpecialty(professional.getSpecialty());
                }
                if (professional.getEmail() != null) {
                    existingProfessional.setEmail(professional.getEmail());
                }
                if (professional.getPhoneNumber() != null) {
                    existingProfessional.setPhoneNumber(professional.getPhoneNumber());
                }
                if (professional.getImageUrl() != null) {
                    existingProfessional.setImageUrl(professional.getImageUrl());
                }
                if (professional.getInitials() != null) {
                    existingProfessional.setInitials(professional.getInitials());
                }
                if (professional.getLocation() != null) {
                    existingProfessional.setLocation(professional.getLocation());
                }
                if (professional.getTeamId() != null) {
                    existingProfessional.setTeamId(professional.getTeamId());
                }
                if (professional.getCreatedDate() != null) {
                    existingProfessional.setCreatedDate(professional.getCreatedDate());
                }
                if (professional.getModifiedDate() != null) {
                    existingProfessional.setModifiedDate(professional.getModifiedDate());
                }
                if (professional.getCreatedBy() != null) {
                    existingProfessional.setCreatedBy(professional.getCreatedBy());
                }
                if (professional.getModifiedBy() != null) {
                    existingProfessional.setModifiedBy(professional.getModifiedBy());
                }

                return existingProfessional;
            })
            .map(professionalRepository::save);
    }

    /**
     * Get all the professionals.
     *
     * @return the list of entities.
     */
    public List<Professional> findAll() {
        log.debug("Request to get all Professionals");
        return professionalRepository.findAll();
    }

    /**
     * Get one professional by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Professional> findOne(String id) {
        log.debug("Request to get Professional : {}", id);
        return professionalRepository.findById(id);
    }

    /**
     * Delete the professional by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Professional : {}", id);
        professionalRepository.deleteById(id);
    }
}
