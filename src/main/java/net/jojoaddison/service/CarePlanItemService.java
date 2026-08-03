package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.repository.CarePlanItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.CarePlanItem}.
 */
@Service
public class CarePlanItemService {

    private final Logger log = LoggerFactory.getLogger(CarePlanItemService.class);

    private final CarePlanItemRepository carePlanItemRepository;

    public CarePlanItemService(CarePlanItemRepository carePlanItemRepository) {
        this.carePlanItemRepository = carePlanItemRepository;
    }

    /**
     * Save a carePlanItem.
     *
     * @param carePlanItem the entity to save.
     * @return the persisted entity.
     */
    public CarePlanItem save(CarePlanItem carePlanItem) {
        log.debug("Request to save CarePlanItem : {}", carePlanItem);
        return carePlanItemRepository.save(carePlanItem);
    }

    /**
     * Update a carePlanItem.
     *
     * @param carePlanItem the entity to save.
     * @return the persisted entity.
     */
    public CarePlanItem update(CarePlanItem carePlanItem) {
        log.debug("Request to update CarePlanItem : {}", carePlanItem);
        return carePlanItemRepository.save(carePlanItem);
    }

    /**
     * Partially update a carePlanItem.
     *
     * @param carePlanItem the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<CarePlanItem> partialUpdate(CarePlanItem carePlanItem) {
        log.debug("Request to partially update CarePlanItem : {}", carePlanItem);

        return carePlanItemRepository
            .findById(carePlanItem.getId())
            .map(existingCarePlanItem -> {
                if (carePlanItem.getPatientId() != null) {
                    existingCarePlanItem.setPatientId(carePlanItem.getPatientId());
                }
                if (carePlanItem.getPlanType() != null) {
                    existingCarePlanItem.setPlanType(carePlanItem.getPlanType());
                }
                if (carePlanItem.getLabel() != null) {
                    existingCarePlanItem.setLabel(carePlanItem.getLabel());
                }
                if (carePlanItem.getDetail() != null) {
                    existingCarePlanItem.setDetail(carePlanItem.getDetail());
                }
                if (carePlanItem.getCadence() != null) {
                    existingCarePlanItem.setCadence(carePlanItem.getCadence());
                }
                if (carePlanItem.getCompleted() != null) {
                    existingCarePlanItem.setCompleted(carePlanItem.getCompleted());
                }
                if (carePlanItem.getSortOrder() != null) {
                    existingCarePlanItem.setSortOrder(carePlanItem.getSortOrder());
                }
                if (carePlanItem.getCreatedDate() != null) {
                    existingCarePlanItem.setCreatedDate(carePlanItem.getCreatedDate());
                }
                if (carePlanItem.getModifiedDate() != null) {
                    existingCarePlanItem.setModifiedDate(carePlanItem.getModifiedDate());
                }
                if (carePlanItem.getCreatedBy() != null) {
                    existingCarePlanItem.setCreatedBy(carePlanItem.getCreatedBy());
                }
                if (carePlanItem.getModifiedBy() != null) {
                    existingCarePlanItem.setModifiedBy(carePlanItem.getModifiedBy());
                }

                return existingCarePlanItem;
            })
            .map(carePlanItemRepository::save);
    }

    /**
     * Get all the carePlanItems.
     *
     * @return the list of entities.
     */
    public List<CarePlanItem> findAll() {
        log.debug("Request to get all CarePlanItems");
        return carePlanItemRepository.findAll();
    }

    /**
     * Get one carePlanItem by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<CarePlanItem> findOne(String id) {
        log.debug("Request to get CarePlanItem : {}", id);
        return carePlanItemRepository.findById(id);
    }

    /**
     * Delete the carePlanItem by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete CarePlanItem : {}", id);
        carePlanItemRepository.deleteById(id);
    }
}
