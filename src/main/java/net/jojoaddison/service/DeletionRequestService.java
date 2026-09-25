package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import net.jojoaddison.repository.DeletionRequestRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.event.EntityEventPublisher;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
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
    private final PatientEventPublisher events;
    private final ProfileRepository profileRepository;

    /**
     * The second channel, since backlog item 46 — the same announcement, on {@code patient.event}.
     *
     * <p>Not a replacement for {@code events}: both are published on every transition, so the gateway's mail router and
     * its account closer can be rebound to one channel per product without a window in which an erasure is announced to
     * nobody. See {@link #announce(DeletionRequest, String, String)}.</p>
     */
    private final EntityEventPublisher entityEvents;

    public DeletionRequestService(
        DeletionRequestRepository deletionRequestRepository,
        PatientErasureService patientErasureService,
        PatientEventPublisher events,
        ProfileRepository profileRepository,
        EntityEventPublisher entityEvents
    ) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.patientErasureService = patientErasureService;
        this.events = events;
        this.profileRepository = profileRepository;
        this.entityEvents = entityEvents;
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
        DeletionRequest saved = deletionRequestRepository.save(request);
        announce(saved, "RAISED");
        return saved;
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
        DeletionRequest saved = deletionRequestRepository.save(request);
        announce(saved, "CANCELLED");
        return saved;
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

        // RESOLVED BEFORE THE ERASURE, DELIBERATELY, AND THE ORDER IS THE WHOLE POINT. The profile that carries the
        // account id is one of the documents `erase` destroys, so resolving inside `announce` — after it — finds
        // nothing and publishes a COMPLETED frame with no join key on it. That is not honesty about a deleted record,
        // it is a lookup sequenced to fail: the same frame carries `requestedByEmail`, a stored-at-raise copy that
        // outlives the record and is more identifying than an opaque User.id, so "do not name an erased subject"
        // cannot be the principle here.
        //
        // It matters on this transition more than any other. COMPLETED is the one frame a consumer must ACT on rather
        // than record — hc-admin deactivates the account and drops its own copy — and a consumer that cannot name the
        // subject cannot act, which leaves data a patient asked to have erased sitting in another product. Resolving
        // here costs one read that was happening anyway, three lines earlier.
        //
        // Publishing still happens after the erasure. The order javadoc on `announce` is about the PUBLISH — a frame
        // announcing a completion that could still fail — and that reasoning is untouched.
        String subjectAccountId = subjectAccountId(request);

        Map<String, Long> erased = patientErasureService.erase(request.getPatientId(), request.getRequestedByEmail());

        request.setStatus(DeletionRequestStatus.COMPLETED);
        request.setCompletedAt(Instant.now());
        request.setCompletedByLogin(adminLogin);
        request.setErasedCounts(erased);
        LOG.info("Deletion request {} completed by {} for patient {}", request.getId(), adminLogin, request.getPatientId());
        DeletionRequest saved = deletionRequestRepository.save(request);
        announce(saved, "COMPLETED", subjectAccountId);
        return saved;
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
        DeletionRequest saved = deletionRequestRepository.save(request);
        announce(saved, "REJECTED");
        return saved;
    }

    /**
     * Says on {@code patient-events} that a deletion request moved, so the gateway can write to the patient.
     *
     * <p><b>This service can neither send mail nor close an account</b>, and both are owed. Until 2026-08-31 a
     * patient raised a request and then heard nothing at all — not when it was carried out, not when it was
     * refused — which for the one irreversible thing they can ask for is the worst place in the product to be
     * silent. Same shape as {@code CareDelegationChanged}: this service knows what happened, only the gateway can
     * tell anybody.</p>
     *
     * <p><b>The email is read off the request, never looked up.</b> For {@code COMPLETED} the erasure has already
     * taken the {@code Profile}, so there is nothing left to resolve — {@code requestedByEmail} was stored at
     * {@link #raise} precisely so this still works afterwards. Publishing before the erasure instead would have
     * announced a completion that could still fail.</p>
     *
     * <p><b>What is deliberately not carried.</b> No {@code erasedCounts}: how many medications a patient had is a
     * fact about their record, and §8.4 says an event reports that something happened, never what it said. No
     * {@code decisionReason} either — an administrator's free text is unbounded and could hold anything, and the
     * patient can read it on their own request through {@code GET /api/deletion-requests/mine}. The mail says a
     * decision was made and points them at it.</p>
     *
     * <p>Failure posture is {@link PatientEventPublisher}'s: publishing never fails the operation. Worth restating
     * here because this is the case where it is most tempting to "fix" into a bug — by the time this runs the
     * erasure has happened and the request is saved. <b>The record is already gone; the event is a notification,
     * never the mechanism.</b> A mail failure must not resurrect it.</p>
     */
    private void announce(DeletionRequest request, String change) {
        // Every transition but COMPLETED still has its profile, so resolving here is correct for them.
        announce(request, change, subjectAccountId(request));
    }

    /**
     * As above, for the one transition that must resolve its subject before the work rather than after it.
     *
     * <h2>Announced on both channels since backlog item 46</h2>
     *
     * <p>The {@code patient-events} frame is <strong>unchanged</strong> — hc-admin's consumer and the gateway's mail
     * router and account closer all still read it. The second call puts the same transition on {@code patient.event},
     * the channel the estate is consolidating on. ⚠ The new frame does not carry {@code requestId}, because that
     * <em>is</em> {@code subject.entityId} there.</p>
     *
     * <p>⛔ <strong>Every value the three gateway handlers read travels on both topics; two of them travel in a
     * different place, and this comment claimed otherwise.</strong> {@code change} and {@code dueAt} are in
     * {@code data} on both. The address is {@code subject.email} on {@code patient-events} and
     * {@code data.patientEmail} here, because item 124 settled that this channel's {@code subject} is the record. All
     * three of {@code DeletionRequestMailer}, {@code DeletionAccountCloser} and {@code CareDelegationMailer} read
     * {@code event.subject().email()}, and {@code PatientEvent.Subject} carries
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} — so repointing the binding at this topic
     * <strong>binds silently and hands every handler a null recipient</strong> rather than failing. The four deletion
     * mails stop at a WARN, and <em>no gateway account is deactivated after an erasure</em>: {@code
     * DeletionAccountCloser} logs "An erasure completed with no address on the event — no account was closed" and
     * returns, which leaves a live login against an erased record. The gateway must move all three handlers to
     * {@code data.patientEmail} <em>in the same commit as the rebind</em>.</p>
     *
     * @param subjectAccountId resolved by the caller. {@link #complete} reads it before {@code erase} destroys the
     *     profile that holds it; see the comment there for why the order is load-bearing rather than incidental.
     */
    private void announce(DeletionRequest request, String change, String subjectAccountId) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.getId());
        data.put("change", change);
        if (request.getDueAt() != null) {
            data.put("dueAt", request.getDueAt().toString());
        }
        events.publish(
            PatientEventType.DELETION_REQUEST_CHANGED,
            request.getRequestedByEmail(),
            request.getRequestedByLogin(),
            subjectAccountId,
            data
        );
        // The same announcement on patient.event. The address is still the stored-at-raise copy and never a lookup —
        // for COMPLETED the profile that would have answered one is already gone.
        entityEvents.publishDeletionRequestChanged(request.getId(), change, request.getRequestedByEmail(), request.getDueAt());
    }

    /**
     * The gateway account id for the subject's frames, or null when this service cannot name one.
     *
     * <p>Resolved at announce time because the request does not store it — {@code requestedByEmail} is stored at
     * raise precisely because the erasure takes the {@code Profile}, and the account id has no such copy. So the
     * {@code COMPLETED} frame, published after the erasure, carries {@code null} here by construction: the profile
     * that held the link is gone, and null honestly says so where a stored copy would outlive the record it names.
     * The other three transitions resolve normally. Guarded like {@code MembershipService}'s lookup, and for the
     * same reason — this Mongo query runs after the state change is saved, so a database hiccup must cost the
     * subject field and never the operation.</p>
     */
    private String subjectAccountId(DeletionRequest request) {
        if (request.getPatientId() == null) {
            return null;
        }
        try {
            return profileRepository
                .findByPatientId(request.getPatientId())
                .stream()
                .findFirst()
                .or(() -> profileRepository.findById(request.getPatientId()))
                .map(Profile::getAccountId)
                .orElse(null);
        } catch (Exception e) {
            LOG.warn("Could not resolve an account id for patient {} — the request is unaffected", request.getPatientId(), e);
            return null;
        }
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
