package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.CarePlanItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the CarePlanItem entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CarePlanItemRepository extends MongoRepository<CarePlanItem, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<CarePlanItem> findByPatientId(String patientId);

    Page<CarePlanItem> findByPatientId(String patientId, Pageable pageable);

    /**
     * Live records only.
     *
     * <p>{@code IsNull} rather than a boolean test, and it is load-bearing for the data that already exists: every
     * document written before the archive fields has no {@code archived_at} key at all, and in MongoDB a null match
     * also matches a missing field, so they all read as live with no migration.</p>
     */
    List<CarePlanItem> findByPatientIdAndArchivedAtIsNull(String patientId);

    List<CarePlanItem> findByArchivedAtIsNull();
}
