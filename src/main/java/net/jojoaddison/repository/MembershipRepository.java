package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.enumeration.MembershipStatus;
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

    /**
     * One patient's memberships in one state.
     *
     * <p>Added for the inbound {@code patient-events-plan} consumer, which is handed an email and has to decide which
     * membership a verification applies to. Item 19 settled that as <em>the patient's single {@code PENDING}
     * membership, refusing rather than guessing</em> — so the caller needs the whole set to count it, not the first
     * match. Returning a {@code List} where an {@code Optional} would read more neatly is the point: a second pending
     * membership is the case that must be seen rather than silently discarded.</p>
     */
    List<Membership> findByPatientIdAndStatus(String patientId, MembershipStatus status);
}
