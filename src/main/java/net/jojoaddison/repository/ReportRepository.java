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
}
