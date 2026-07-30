package net.jojoaddison.repository;

import net.jojoaddison.domain.ClinicalCase;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ClinicalCase entity.
 */
@Repository
public interface ClinicalCaseRepository extends MongoRepository<ClinicalCase, String> {}
