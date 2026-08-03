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
}
