package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Medication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Medication entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MedicationRepository extends MongoRepository<Medication, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Medication> findByPatientId(String patientId);

    Page<Medication> findByPatientId(String patientId, Pageable pageable);
}
