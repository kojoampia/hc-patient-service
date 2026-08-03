package net.jojoaddison.repository;

import net.jojoaddison.domain.Professional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Professional entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProfessionalRepository extends MongoRepository<Professional, String> {}
