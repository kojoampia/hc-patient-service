package net.jojoaddison.repository;

import net.jojoaddison.domain.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Recommendation entity.
 */
@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {}
