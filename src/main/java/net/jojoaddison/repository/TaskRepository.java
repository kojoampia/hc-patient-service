package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Task entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Task> findByPatientId(String patientId);

    Page<Task> findByPatientId(String patientId, Pageable pageable);

    /**
     * Live records only.
     *
     * <p>{@code IsNull} rather than a boolean test, and it is load-bearing for data that already exists: every
     * document written before the archive fields has no {@code archived_at} key at all, and in MongoDB a null match
     * also matches a missing field, so they all read as live with no migration.</p>
     */
    List<Task> findByPatientIdAndArchivedAtIsNull(String patientId);

    List<Task> findByArchivedAtIsNull();
}
