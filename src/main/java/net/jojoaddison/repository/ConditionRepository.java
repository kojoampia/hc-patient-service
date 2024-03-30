package net.jojoaddison.repository;

import net.jojoaddison.domain.Condition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Condition entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConditionRepository extends MongoRepository<Condition, String> {}
