package net.jojoaddison.repository;

import net.jojoaddison.domain.MedCase;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the MedCase entity.
 */
@Repository
public interface MedCaseRepository extends MongoRepository<MedCase, String> {}
