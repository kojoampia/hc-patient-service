package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.ActivityLog}.
 */
@Service
public class ActivityLogService {

    private final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Save a activityLog.
     *
     * @param activityLog the entity to save.
     * @return the persisted entity.
     */
    public ActivityLog save(ActivityLog activityLog) {
        log.debug("Request to save ActivityLog : {}", activityLog);
        return activityLogRepository.save(activityLog);
    }

    /**
     * Update a activityLog.
     *
     * @param activityLog the entity to save.
     * @return the persisted entity.
     */
    public ActivityLog update(ActivityLog activityLog) {
        log.debug("Request to update ActivityLog : {}", activityLog);
        return activityLogRepository.save(activityLog);
    }

    /**
     * Partially update a activityLog.
     *
     * @param activityLog the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ActivityLog> partialUpdate(ActivityLog activityLog) {
        log.debug("Request to partially update ActivityLog : {}", activityLog);

        return activityLogRepository
            .findById(activityLog.getId())
            .map(existingActivityLog -> {
                if (activityLog.getPatientId() != null) {
                    existingActivityLog.setPatientId(activityLog.getPatientId());
                }
                if (activityLog.getCaseId() != null) {
                    existingActivityLog.setCaseId(activityLog.getCaseId());
                }
                if (activityLog.getLoggedAt() != null) {
                    existingActivityLog.setLoggedAt(activityLog.getLoggedAt());
                }
                if (activityLog.getSummary() != null) {
                    existingActivityLog.setSummary(activityLog.getSummary());
                }
                if (activityLog.getDetail() != null) {
                    existingActivityLog.setDetail(activityLog.getDetail());
                }
                if (activityLog.getKind() != null) {
                    existingActivityLog.setKind(activityLog.getKind());
                }
                if (activityLog.getSource() != null) {
                    existingActivityLog.setSource(activityLog.getSource());
                }
                if (activityLog.getAuthorId() != null) {
                    existingActivityLog.setAuthorId(activityLog.getAuthorId());
                }
                if (activityLog.getCreatedDate() != null) {
                    existingActivityLog.setCreatedDate(activityLog.getCreatedDate());
                }
                if (activityLog.getCreatedBy() != null) {
                    existingActivityLog.setCreatedBy(activityLog.getCreatedBy());
                }

                return existingActivityLog;
            })
            .map(activityLogRepository::save);
    }

    /**
     * Get all the activityLogs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<ActivityLog> findAll(Pageable pageable) {
        log.debug("Request to get all ActivityLogs");
        return activityLogRepository.findAll(pageable);
    }

    /**
     * Get one activityLog by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<ActivityLog> findOne(String id) {
        log.debug("Request to get ActivityLog : {}", id);
        return activityLogRepository.findById(id);
    }

    /**
     * Delete the activityLog by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete ActivityLog : {}", id);
        activityLogRepository.deleteById(id);
    }
}
