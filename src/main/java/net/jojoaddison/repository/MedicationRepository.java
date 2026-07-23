package net.jojoaddison.repository;

import net.jojoaddison.domain.Medication;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Medication entity.
 */
@Repository
public interface MedicationRepository extends MongoRepository<Medication, String> {}
