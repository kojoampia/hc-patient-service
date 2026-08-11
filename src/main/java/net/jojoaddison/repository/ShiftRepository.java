package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Shift;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Shift entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShiftRepository extends MongoRepository<Shift, String> {
    /**
     * The shifts on one roster. Every roster screen is scoped this way, so the filter belongs in the
     * query rather than in the caller.
     */
    List<Shift> findByRosterId(String rosterId);

    /**
     * The shifts one professional is rostered for, across every roster.
     */
    List<Shift> findByProfessionalId(String professionalId);
}
