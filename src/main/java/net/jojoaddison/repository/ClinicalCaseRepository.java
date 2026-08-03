package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.ClinicalCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the ClinicalCase entity.
 */
@Repository
public interface ClinicalCaseRepository extends MongoRepository<ClinicalCase, String> {
    @Query("{}")
    Page<ClinicalCase> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<ClinicalCase> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<ClinicalCase> findOneWithEagerRelationships(String id);

    /**
     * Cases belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<ClinicalCase> findByPatientId(String patientId);

    Page<ClinicalCase> findByPatientId(String patientId, Pageable pageable);
}
