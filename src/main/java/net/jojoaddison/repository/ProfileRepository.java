package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Profile> findByPatientId(String patientId);

    Page<Profile> findByPatientId(String patientId, Pageable pageable);

    /**
     * The signed-in user's own profile. The gateway issues tokens keyed on email, and email is the
     * only thing the dashboard knows about the person before it has loaded anything.
     */
    Optional<Profile> findOneByEmailIgnoreCase(String email);
}
