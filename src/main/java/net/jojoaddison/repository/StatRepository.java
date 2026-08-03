package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Stat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Stat entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatRepository extends MongoRepository<Stat, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Stat> findByPatientId(String patientId);

    Page<Stat> findByPatientId(String patientId, Pageable pageable);
}
