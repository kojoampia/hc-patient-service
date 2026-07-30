package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationService.class);

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
        LOG.debug("Request to save Recommendation : {}", recommendation);
        return recommendationRepository.save(recommendation);
    }

    /**
     * Update a recommendation.
     *
     * @param recommendation the entity to save.
     * @return the persisted entity.
     */
    public Recommendation update(Recommendation recommendation) {
        LOG.debug("Request to update Recommendation : {}", recommendation);
        return recommendationRepository.save(recommendation);
    }

    /**
     * Partially update a recommendation.
     *
     * @param recommendation the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Recommendation> partialUpdate(Recommendation recommendation) {
        LOG.debug("Request to partially update Recommendation : {}", recommendation);

        return recommendationRepository
            .findById(recommendation.getId())
            .map(existingRecommendation -> {
                updateIfPresent(existingRecommendation::setLabel, recommendation.getLabel());
                updateIfPresent(existingRecommendation::setCategory, recommendation.getCategory());

                return existingRecommendation;
            })
            .map(recommendationRepository::save);
    }

    /**
     * Get all the recommendations.
     *
     * <p>Unpaged, unlike {@code ClinicalCaseService}: Recommendation.json sets {@code "pagination": "no"}, and the
     * set is a small controlled vocabulary rather than per-patient data.</p>
     *
     * @return the list of entities.
     */
    public List<Recommendation> findAll() {
        LOG.debug("Request to get all Recommendations");
        return recommendationRepository.findAll();
    }

    /**
     * Get one recommendation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Recommendation> findOne(String id) {
        LOG.debug("Request to get Recommendation : {}", id);
        return recommendationRepository.findById(id);
    }

    /**
     * Delete the recommendation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Recommendation : {}", id);
        recommendationRepository.deleteById(id);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
