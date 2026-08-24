package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Report entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ReportRepository extends MongoRepository<Report, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Report> findByPatientId(String patientId);

    Page<Report> findByPatientId(String patientId, Pageable pageable);

    /**
     * Live records only.
     *
     * <p>{@code IsNull} rather than a boolean test, and it is load-bearing for data that already exists: every
     * document written before the archive fields has no {@code archived_at} key at all, and in MongoDB a null match
     * also matches a missing field, so they all read as live with no migration.</p>
     */
    List<Report> findByPatientIdAndArchivedAtIsNull(String patientId);

    List<Report> findByArchivedAtIsNull();
}
