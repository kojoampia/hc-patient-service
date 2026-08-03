package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Visitation;
import net.jojoaddison.repository.VisitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Visitation}.
 */
@Service
public class VisitationService {

    private final Logger log = LoggerFactory.getLogger(VisitationService.class);

    private final VisitationRepository visitationRepository;

    public VisitationService(VisitationRepository visitationRepository) {
        this.visitationRepository = visitationRepository;
    }

    /**
     * Save a visitation.
     *
     * @param visitation the entity to save.
     * @return the persisted entity.
     */
    public Visitation save(Visitation visitation) {
        log.debug("Request to save Visitation : {}", visitation);
        return visitationRepository.save(visitation);
    }

    /**
     * Update a visitation.
     *
     * @param visitation the entity to save.
     * @return the persisted entity.
     */
    public Visitation update(Visitation visitation) {
        log.debug("Request to update Visitation : {}", visitation);
        return visitationRepository.save(visitation);
    }

    /**
     * Partially update a visitation.
     *
     * @param visitation the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Visitation> partialUpdate(Visitation visitation) {
        log.debug("Request to partially update Visitation : {}", visitation);

        return visitationRepository
            .findById(visitation.getId())
            .map(existingVisitation -> {
                if (visitation.getPatientId() != null) {
                    existingVisitation.setPatientId(visitation.getPatientId());
                }
                if (visitation.getCaseId() != null) {
                    existingVisitation.setCaseId(visitation.getCaseId());
                }
                if (visitation.getProfessionalId() != null) {
                    existingVisitation.setProfessionalId(visitation.getProfessionalId());
                }
                if (visitation.getVisitedAt() != null) {
                    existingVisitation.setVisitedAt(visitation.getVisitedAt());
                }
                if (visitation.getPurpose() != null) {
                    existingVisitation.setPurpose(visitation.getPurpose());
                }
                if (visitation.getLocation() != null) {
                    existingVisitation.setLocation(visitation.getLocation());
                }
                if (visitation.getNotes() != null) {
                    existingVisitation.setNotes(visitation.getNotes());
                }
                if (visitation.getCreatedDate() != null) {
                    existingVisitation.setCreatedDate(visitation.getCreatedDate());
                }
                if (visitation.getModifiedDate() != null) {
                    existingVisitation.setModifiedDate(visitation.getModifiedDate());
                }
                if (visitation.getCreatedBy() != null) {
                    existingVisitation.setCreatedBy(visitation.getCreatedBy());
                }
                if (visitation.getModifiedBy() != null) {
                    existingVisitation.setModifiedBy(visitation.getModifiedBy());
                }

                return existingVisitation;
            })
            .map(visitationRepository::save);
    }

    /**
     * Get all the visitations.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<Visitation> findAll(Pageable pageable) {
        log.debug("Request to get all Visitations");
        return visitationRepository.findAll(pageable);
    }

    /**
     * Get one visitation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Visitation> findOne(String id) {
        log.debug("Request to get Visitation : {}", id);
        return visitationRepository.findById(id);
    }

    /**
     * Delete the visitation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Visitation : {}", id);
        visitationRepository.deleteById(id);
    }
}
