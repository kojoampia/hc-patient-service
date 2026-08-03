package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Recommendation}.
 */
@Service
public class RecommendationService {

    private final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recommendationRepository;

    public RecommendationService(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    /**
     * Save a recommendation.
     *
     * @param recommendation the entity to save.
     * @return the persisted entity.
     */
    public Recommendation save(Recommendation recommendation) {
        log.debug("Request to save Recommendation : {}", recommendation);
        return recommendationRepository.save(recommendation);
    }

    /**
     * Update a recommendation.
     *
     * @param recommendation the entity to save.
     * @return the persisted entity.
     */
    public Recommendation update(Recommendation recommendation) {
        log.debug("Request to update Recommendation : {}", recommendation);
        return recommendationRepository.save(recommendation);
    }

    /**
     * Partially update a recommendation.
     *
     * @param recommendation the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Recommendation> partialUpdate(Recommendation recommendation) {
        log.debug("Request to partially update Recommendation : {}", recommendation);

        return recommendationRepository
            .findById(recommendation.getId())
            .map(existingRecommendation -> {
                if (recommendation.getLabel() != null) {
                    existingRecommendation.setLabel(recommendation.getLabel());
                }
                if (recommendation.getCategory() != null) {
                    existingRecommendation.setCategory(recommendation.getCategory());
                }

                return existingRecommendation;
            })
            .map(recommendationRepository::save);
    }

    /**
     * Get all the recommendations.
     *
     * @return the list of entities.
     */
    public List<Recommendation> findAll() {
        log.debug("Request to get all Recommendations");
        return recommendationRepository.findAll();
    }

    /**
     * Get one recommendation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Recommendation> findOne(String id) {
        log.debug("Request to get Recommendation : {}", id);
        return recommendationRepository.findById(id);
    }

    /**
     * Delete the recommendation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Recommendation : {}", id);
        recommendationRepository.deleteById(id);
    }
}
