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

    /**
     * The same, minus anything archived — what a working queue asks for.
     *
     * <p>{@code IsNull} rather than a boolean test, and it matters for the data that already exists: in MongoDB a
     * null match also matches documents where the field is <em>absent</em>, so every case written before archiving
     * existed reads as live without a migration.</p>
     */
    List<ClinicalCase> findByPatientIdAndArchivedAtIsNull(String patientId);

    Page<ClinicalCase> findByPatientIdAndArchivedAtIsNull(String patientId, Pageable pageable);

    Page<ClinicalCase> findByArchivedAtIsNull(Pageable pageable);

    /**
     * The unscoped live query, in the eager-loading form the unrestricted branch of the list endpoint uses.
     *
     * <p>Written against the stored field name, {@code archived_at}, because that is what {@code @Field} maps
     * {@code archivedAt} onto and a {@code @Query} is a raw document filter rather than a derived one.</p>
     */
    @Query("{'archived_at': null}")
    Page<ClinicalCase> findAllLiveWithEagerRelationships(Pageable pageable);
}
