package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.DutyRoster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DutyRoster entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DutyRosterRepository extends MongoRepository<DutyRoster, String> {
    /**
     * The rosters one professional follows. Matches against the {@code subscribedProfessionalIds}
     * array, so a roster the professional has shifts on but has not subscribed to is not returned.
     */
    List<DutyRoster> findBySubscribedProfessionalIdsContains(String professionalId);
}
