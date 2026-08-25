package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DeletionRequest entity.
 *
 * <p>{@link #findOneByPatientIdAndStatus} is asked twice per raise — once to refuse a duplicate, once by the clients
 * to decide whether to show the pending banner — which is why {@code DeletionRequest} carries a compound index on
 * {@code (patient_id, status)}.</p>
 */
@SuppressWarnings("unused")
@Repository
public interface DeletionRequestRepository extends MongoRepository<DeletionRequest, String> {
    /**
     * This patient's request in a given state, if there is one.
     *
     * <p>Only ever one {@code PENDING} per patient — {@code DeletionRequestService.raise} refuses a second. Nothing
     * at the database level enforces that, so a partial unique index would be the belt to this braces if concurrent
     * raises ever became plausible; today the only client that can raise one is the patient's own session.</p>
     */
    Optional<DeletionRequest> findOneByPatientIdAndStatus(String patientId, DeletionRequestStatus status);

    /** Everything ever asked for over this record, newest first — including after the record itself is gone. */
    List<DeletionRequest> findByPatientIdOrderByRequestedAtDesc(String patientId);

    /** The administrator's queue. Callers pass {@code PENDING} and sort by {@code dueAt}. */
    Page<DeletionRequest> findByStatus(DeletionRequestStatus status, Pageable pageable);
}
