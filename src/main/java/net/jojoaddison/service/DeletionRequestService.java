package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import net.jojoaddison.repository.DeletionRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The lifecycle of a {@link DeletionRequest}: raising one, withdrawing it, and the two ways an administrator can
 * close it.
 *
 * <h2>The rules live here, not in the resource</h2>
 *
 * <p>Every transition below refuses a request that is not {@code PENDING}, and each refusal is a
 * {@link DomainStateException} rather than an HTTP concern, because "a completed erasure cannot be cancelled" is true
 * however the caller arrived. The resource above this decides <em>who</em> may ask; this decides <em>whether the
 * document is in a state where the answer can be yes</em>. Both checks are needed and neither substitutes for the
 * other.</p>
 *
 * <h2>Who may call what</h2>
 *
 * <p>{@link #raise} and {@link #cancel} are the patient's. {@link #complete} and {@link #reject} are
 * {@code ROLE_ADMIN}'s, enforced by {@code @PreAuthorize} on {@code DeletionRequestResource} — this class is not the
 * authorization boundary and must not be mistaken for one.</p>
 */
@Service
public class DeletionRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(DeletionRequestService.class);

    private static final String ENTITY_NAME = "patientServiceDeletionRequest";

    /**
     * The window published in the privacy policy: a record is erased within fourteen days of being asked for.
     *
     * <p>It is a maximum owed to the patient and a cooling-off period in the same fourteen days — an administrator may
     * complete a request the day it arrives, and the patient may withdraw it up until they do.</p>
     *
     * <p><strong>Changing this number changes a published promise.</strong> The policy text at
     * {@code web.abofonsa.com/privacy} states it, all three client locales state it, and every request already raised
     * keeps the {@code dueAt} it was given — see {@link DeletionRequest#getDueAt()}. Those four are the things that
     * have to move together.</p>
     *
     * <p>The host matters and this javadoc had it wrong until 2026-08-31. {@code abofonsa.com} is the
     * launch-preview site, whose SPA fallback answers <b>200 with a countdown page</b> for any path — so the URL
     * cited here as the place the promise is published served no promise at all, and both clients linked patients
     * to it from the delete-my-record screen. The policy is on {@code web.abofonsa.com}. Verify that kind of claim
     * by reading the page, never by its status code.</p>
     */
    public static final Duration WINDOW = Duration.ofDays(14);

    private final DeletionRequestRepository deletionRequestRepository;
    private final PatientErasureService patientErasureService;

    public DeletionRequestService(DeletionRequestRepository deletionRequestRepository, PatientErasureService patientErasureService) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.patientErasureService = patientErasureService;
    }

    /**
     * Records a patient's request to be erased.
     *
     * @param patientId the record to erase, resolved from the caller's own token — never from the payload.
     * @param email the requesting account's email.
     * @param login the requesting account's gateway login.
     * @param reason the patient's own words, optional.
     * @return the stored request, {@code PENDING}, carrying the date the erasure is owed by.
     * @throws DomainStateException if this patient already has a pending request.
     */
    public DeletionRequest raise(String patientId, String email, String login, String reason) {
        deletionRequestRepository
            .findOneByPatientIdAndStatus(patientId, DeletionRequestStatus.PENDING)
            .ifPresent(existing -> {
                throw new DomainStateException("A deletion request is already pending for this record", ENTITY_NAME, "requestpending");
            });

        Instant now = Instant.now();
        DeletionRequest request = new DeletionRequest()
            .patientId(patientId)
            .requestedByEmail(email)
            .requestedByLogin(login)
            .reason(reason)
            .status(DeletionRequestStatus.PENDING)
            .requestedAt(now)
            .dueAt(now.plus(WINDOW));

        LOG.info("Deletion requested for patient {} — due by {}", patientId, request.getDueAt());
        return deletionRequestRepository.save(request);
    }

    /**
     * Withdraws a request during the cooling-off window.
     *
     * @param request the request to withdraw.
     * @return it, {@code CANCELLED}.
     * @throws DomainStateException if it is not pending.
     */
    public DeletionRequest cancel(DeletionRequest request) {
        requirePending(request, "cancelled");
        request.setStatus(DeletionRequestStatus.CANCELLED);
        request.setCancelledAt(Instant.now());
        LOG.info("Deletion request {} withdrawn for patient {}", request.getId(), request.getPatientId());
        return deletionRequestRepository.save(request);
    }

    /**
     * Carries out the erasure. {@code ROLE_ADMIN} only — see the class comment.
     *
     * <p>The order is load-bearing. The erasure runs first and the request is marked {@code COMPLETED} only once it
     * returns, so a failure part-way leaves a {@code PENDING} request — a job still on the queue — rather than a
     * promise recorded as kept. {@link PatientErasureService} is safe to re-run for exactly this reason.</p>
     *
     * @param request the request to fulfil.
     * @param adminLogin the administrator carrying it out.
     * @return it, {@code COMPLETED}, carrying what was removed.
     * @throws DomainStateException if it is not pending.
     */
    public DeletionRequest complete(DeletionRequest request, String adminLogin) {
        requirePending(request, "completed");

        Map<String, Long> erased = patientErasureService.erase(request.getPatientId(), request.getRequestedByEmail());

        request.setStatus(DeletionRequestStatus.COMPLETED);
        request.setCompletedAt(Instant.now());
        request.setCompletedByLogin(adminLogin);
        request.setErasedCounts(erased);
        LOG.info("Deletion request {} completed by {} for patient {}", request.getId(), adminLogin, request.getPatientId());
        return deletionRequestRepository.save(request);
    }

    /**
     * Refuses a request, with a reason the patient is owed. {@code ROLE_ADMIN} only.
     *
     * @param request the request to refuse.
     * @param adminLogin the administrator refusing it.
     * @param decisionReason why — required, and rejected when blank.
     * @return it, {@code REJECTED}.
     * @throws DomainStateException if it is not pending, or no reason was given.
     */
    public DeletionRequest reject(DeletionRequest request, String adminLogin, String decisionReason) {
        requirePending(request, "rejected");
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new DomainStateException("A rejection must say why", ENTITY_NAME, "reasonrequired");
        }

        request.setStatus(DeletionRequestStatus.REJECTED);
        request.setRejectedAt(Instant.now());
        request.setRejectedByLogin(adminLogin);
        request.setDecisionReason(decisionReason.trim());
        LOG.info("Deletion request {} rejected by {} for patient {}", request.getId(), adminLogin, request.getPatientId());
        return deletionRequestRepository.save(request);
    }

    /** This patient's open request, if they have one. What the clients ask on sign-in. */
    public Optional<DeletionRequest> pendingFor(String patientId) {
        return deletionRequestRepository.findOneByPatientIdAndStatus(patientId, DeletionRequestStatus.PENDING);
    }

    /** The administrator's queue. */
    public Page<DeletionRequest> findByStatus(DeletionRequestStatus status, Pageable pageable) {
        return deletionRequestRepository.findByStatus(status, pageable);
    }

    public Optional<DeletionRequest> findOne(String id) {
        return deletionRequestRepository.findById(id);
    }

    private void requirePending(DeletionRequest request, String verb) {
        if (!request.isPending()) {
            throw new DomainStateException("A request that is " + request.getStatus() + " cannot be " + verb, ENTITY_NAME, "notpending");
        }
    }
}
