package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Allergy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Allergy entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AllergyRepository extends MongoRepository<Allergy, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Allergy> findByPatientId(String patientId);

    Page<Allergy> findByPatientId(String patientId, Pageable pageable);
}
