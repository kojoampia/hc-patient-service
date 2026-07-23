package net.jojoaddison.repository;

import net.jojoaddison.domain.Stat;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Stat entity.
 */
@Repository
public interface StatRepository extends MongoRepository<Stat, String> {}
