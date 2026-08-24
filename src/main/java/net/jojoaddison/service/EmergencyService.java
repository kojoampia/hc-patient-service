package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Emergency;
import net.jojoaddison.repository.EmergencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Emergency}.
 */
@Service
public class EmergencyService {

    private static final String ENTITY_NAME = "hcPatientServiceEmergency";

    private final Logger log = LoggerFactory.getLogger(EmergencyService.class);

    private final EmergencyRepository emergencyRepository;

    public EmergencyService(EmergencyRepository emergencyRepository) {
        this.emergencyRepository = emergencyRepository;
    }

    /**
     * Save a emergency.
     *
     * @param emergency the entity to save.
     * @return the persisted entity.
     */
    public Emergency save(Emergency emergency) {
        log.debug("Request to save Emergency : {}", emergency);
        return emergencyRepository.save(emergency);
    }

    /**
     * Update a emergency.
     *
     * @param emergency the entity to save.
     * @return the persisted entity.
     */
    public Emergency update(Emergency emergency) {
        log.debug("Request to update Emergency : {}", emergency);
        return emergencyRepository.save(emergency);
    }

    /**
     * Partially update a emergency.
     *
     * @param emergency the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Emergency> partialUpdate(Emergency emergency) {
        log.debug("Request to partially update Emergency : {}", emergency);

        return emergencyRepository
            .findById(emergency.getId())
            .map(existingEmergency -> {
                if (emergency.getPatientId() != null) {
                    existingEmergency.setPatientId(emergency.getPatientId());
                }
                if (emergency.getCaseId() != null) {
                    existingEmergency.setCaseId(emergency.getCaseId());
                }
                if (emergency.getRaisedAt() != null) {
                    existingEmergency.setRaisedAt(emergency.getRaisedAt());
                }
                if (emergency.getResolvedAt() != null) {
                    existingEmergency.setResolvedAt(emergency.getResolvedAt());
                }
                if (emergency.getBrief() != null) {
                    existingEmergency.setBrief(emergency.getBrief());
                }
                if (emergency.getDetail() != null) {
                    existingEmergency.setDetail(emergency.getDetail());
                }
                if (emergency.getSeverity() != null) {
                    existingEmergency.setSeverity(emergency.getSeverity());
                }
                if (emergency.getStatus() != null) {
                    existingEmergency.setStatus(emergency.getStatus());
                }
                if (emergency.getOutcome() != null) {
                    existingEmergency.setOutcome(emergency.getOutcome());
                }
                if (emergency.getLocation() != null) {
                    existingEmergency.setLocation(emergency.getLocation());
                }
                if (emergency.getRespondentId() != null) {
                    existingEmergency.setRespondentId(emergency.getRespondentId());
                }
                if (emergency.getCreatedDate() != null) {
                    existingEmergency.setCreatedDate(emergency.getCreatedDate());
                }
                if (emergency.getModifiedDate() != null) {
                    existingEmergency.setModifiedDate(emergency.getModifiedDate());
                }
                if (emergency.getCreatedBy() != null) {
                    existingEmergency.setCreatedBy(emergency.getCreatedBy());
                }
                if (emergency.getModifiedBy() != null) {
                    existingEmergency.setModifiedBy(emergency.getModifiedBy());
                }

                return existingEmergency;
            })
            .map(emergencyRepository::save);
    }

    /**
     * Get all the emergencies.
     *
     * @return the list of entities.
     */
    public List<Emergency> findAll() {
        log.debug("Request to get all Emergencies");
        return emergencyRepository.findAll();
    }

    /**
     * Get one emergency by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Emergency> findOne(String id) {
        log.debug("Request to get Emergency : {}", id);
        return emergencyRepository.findById(id);
    }

    /**
     * Delete the emergency by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Emergency : {}", id);
        emergencyRepository.deleteById(id);
    }

    /**
     * Retires a emergency from the working lists. See {@link ArchiveSupport} for why it refuses rather than
     * silently succeeding when the record is already archived.
     */
    public Emergency archive(String id, String professionalId, String reason) {
        log.debug("Request to archive Emergency : {}", id);
        return ArchiveSupport.archive(
            emergencyRepository.findById(id),
            id,
            professionalId,
            reason,
            ENTITY_NAME,
            "emergency",
            emergencyRepository::save
        );
    }

    /** The way back. Archiving without it is a delete with extra steps. */
    public Emergency unarchive(String id) {
        log.debug("Request to unarchive Emergency : {}", id);
        return ArchiveSupport.unarchive(emergencyRepository.findById(id), id, ENTITY_NAME, "emergency", emergencyRepository::save);
    }
}
