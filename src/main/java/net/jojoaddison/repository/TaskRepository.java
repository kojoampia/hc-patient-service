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
}
