package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Membership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Membership entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MembershipRepository extends MongoRepository<Membership, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Membership> findByPatientId(String patientId);

    Page<Membership> findByPatientId(String patientId, Pageable pageable);
}
