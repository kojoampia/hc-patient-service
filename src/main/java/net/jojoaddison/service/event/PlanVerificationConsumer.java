package net.jojoaddison.service.event;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.PlanVerification;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.PlanVerificationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.MembershipService;
import net.jojoaddison.service.event.PlanVerificationRefusedException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * The one consumer bound to {@code patient-events-plan} — hc-admin's acknowledgement that an administrator decided a
 * patient's plan choice — which it applies by moving that patient's pending membership to {@code ACTIVE}.
 *
 * <h2>The first topic this service consumes that it does not own</h2>
 *
 * <p>Everything else here <em>publishes</em>: {@code patient-events} is this subsystem's own stream and both its
 * producers are ours. This is the return leg of the exchange item 18 opened — a patient chooses a tier, this service
 * writes a {@code PENDING} {@link Membership} and announces it, an administrator decides on it next door, and the
 * decision comes back here. Backlog item 19.</p>
 *
 * <pre>
 * topic     patient-events-plan     hc-admin publishes, this repo consumes
 * subject   Patient.email           lower-cased, as every frame on patient-events is
 * payload   { Plan }                one field
 * </pre>
 *
 * <p><b>The method name IS the binding name.</b> Spring Cloud Stream derives {@code patientPlanEventsConsumer-in-0}
 * from the {@code @Bean} method, and {@code spring.cloud.function.definition} names it in YAML — so renaming the
 * method does not fail to compile and does not fail to start. The context comes up, every request is served, and
 * every acknowledgement hc-admin sends is dropped on the floor with nothing thrown and nothing logged at a level
 * anybody watches. {@code PlanVerificationConsumerBindingIT} is the only thing that can see that.</p>
 *
 * <p>And this class is deliberately not called {@code PatientPlanEventsConsumer}: Spring would derive that same bean
 * name for the component itself, and a {@code @Bean} method sharing its own class's bean name is a factory-bean
 * reference pointing at itself. The context refuses to start — the gateway's {@code PatientEventMailRouter} records
 * the same trap.</p>
 *
 * <h2>It does not dispatch on the event type, and that is a decision</h2>
 *
 * <p>Every other consumer in the estate routes on {@code type} and ignores what it does not know. This one applies
 * every frame on the topic, <b>because the type string was never agreed and hc-admin has not written their half.</b>
 * Guessing a literal would reproduce item 18's own fortnight-long half-loop exactly — an event asserted by a literal
 * on one side and falling to a {@code default} case on the other — except inverted and worse: this side would go on
 * consuming, acknowledging and ignoring, and the loop would read as working. The topic carries one exchange and
 * nothing else, so the topic is the contract.</p>
 *
 * <p><b>Revisit this the moment hc-admin's item 54 lands.</b> If they ever put a second kind of frame on
 * {@code patient-events-plan}, dispatch becomes necessary and the type string has to be agreed in the same breath.
 * The type each frame carried is recorded on {@link PlanVerification} so that the question can be answered from the
 * data rather than from a guess.</p>
 *
 * <h2>Refusing, and what reaches the dead-letter queue</h2>
 *
 * <p>Nine ways an acknowledgement can be wrong, enumerated in {@link Reason} and each refused separately so the log
 * says which. <b>Every refusal throws</b>, which dead-letters the frame with its bytes intact and advances the
 * offset: the decision is recoverable by replay once whatever caused it is fixed, where a swallowed one would leave
 * only a log line nothing alerts on. The binding is never at risk — the binder retries, dead-letters and takes the
 * next frame, which {@code PlanVerificationRoundTripIT} proves against a real broker rather than asserting.</p>
 *
 * <p><b>One thing is ignored rather than refused: a frame this service has already applied.</b> That is not a fault,
 * it is at-least-once delivery working as specified, and dead-lettering it would fill the DLQ with successes.</p>
 *
 * <h2>Idempotency</h2>
 *
 * <p>{@link PatientEvent#eventId()} keys {@link PlanVerification}, whose {@code _id} it is — so "have I applied this
 * frame" is a primary-key read, and a replay produces neither a second write nor a second announcement. The ledger is
 * written <b>after</b> the membership, deliberately: a crash in the gap then makes the redelivery refuse
 * ({@link Reason#NO_PENDING_MEMBERSHIP}, since the membership is already {@code ACTIVE}), which is visible in the DLQ
 * and recoverable. Writing the ledger first would make the same crash lose the verification silently, and this file
 * chooses the loud failure every time it has the choice.</p>
 *
 * <p><b>The ledger is patient data and is erased with the patient</b> — {@code PatientErasureService.PATIENT_SCOPED}
 * names it, and it was missed there for one review. So idempotency is bounded by the patient's existence: a
 * redelivery arriving after an erasure is no longer recognised as a replay and refuses
 * {@link Reason#UNKNOWN_PATIENT} instead. Loud, correct, and not a regression — see {@link PlanVerification}.</p>
 *
 * <h2>It idles, and that is the design rather than a defect</h2>
 *
 * <p><b>hc-admin publishes nothing on this topic today</b> — their item 54 is unbuilt, and {@code patient-events-plan}
 * appears in no file of any of their five repositories (measured, not assumed). Item 19 chose this order on purpose:
 * a consumer nobody publishes to idles, loses nothing and drains the backlog the day the producer starts, whereas the
 * opposite experiment — item 18's publish-first — spent a fortnight as a half-loop. Do not add a producer here to
 * make it look alive; the only things that write to this topic are hc-admin and, in tests, a raw Kafka producer in
 * the test itself.</p>
 */
@Component
public class PlanVerificationConsumer {

    private final Logger log = LoggerFactory.getLogger(PlanVerificationConsumer.class);

    private final MembershipService membershipService;

    private final ProfileRepository profileRepository;

    private final PlanVerificationRepository planVerifications;

    public PlanVerificationConsumer(
        MembershipService membershipService,
        ProfileRepository profileRepository,
        PlanVerificationRepository planVerifications
    ) {
        this.membershipService = membershipService;
        this.profileRepository = profileRepository;
        this.planVerifications = planVerifications;
    }

    @Bean
    public Consumer<PatientEvent> patientPlanEventsConsumer() {
        return this::apply;
    }

    /**
     * Applies one acknowledgement, or refuses it.
     *
     * <p>Frame first, then the patient, then the membership — cheap checks before database reads, and a producer
     * defect reported as a producer defect whether or not the patient it names happens to exist here.</p>
     */
    void apply(PatientEvent event) {
        if (event == null) {
            // The binder does not hand a function a null payload, so this is defensive rather than expected. Refused
            // rather than returned, because a silent return would be indistinguishable from a frame applied.
            throw refuse(Reason.NO_EVENT_ID, "a null frame arrived on patient-events-plan");
        }

        String eventId = event.eventId();
        if (isBlank(eventId)) {
            throw refuse(
                Reason.NO_EVENT_ID,
                "an acknowledgement arrived with no eventId, so a replay of it could not be told from a first delivery"
            );
        }

        if (planVerifications.existsById(eventId)) {
            // At-least-once delivery working as specified. Not a fault, so not dead-lettered.
            log.debug("Ignoring plan acknowledgement {} — already applied", eventId);
            return;
        }

        String email = subjectEmail(event);
        String planCode = planCodeIn(event.data());
        if (isBlank(planCode)) {
            throw refuse(
                Reason.NO_PLAN_NAMED,
                "acknowledgement " +
                eventId +
                " for " +
                email +
                " named no plan, so the consistency check its payload exists for cannot be made; its payload keys were " +
                (event.data() == null ? "none" : event.data().keySet())
            );
        }
        assertActivating(eventId, event.data());

        Profile profile = profileRepository
            .findOneByEmailIgnoreCase(email)
            .orElseThrow(() ->
                refuse(Reason.UNKNOWN_PATIENT, "acknowledgement " + eventId + " names " + email + ", who has no profile here")
            );
        // patientId is what the collections are keyed by, but profiles written before the field existed carry only
        // their own id. The same fallback PatientScope applies resolving the other direction.
        String patientId = Optional.ofNullable(profile.getPatientId()).orElse(profile.getId());

        Membership pending = singlePendingMembership(eventId, email, patientId);
        assertPlanAgrees(eventId, email, planCode, pending);

        Membership activated = membershipService
            .activateIfPending(pending.getId())
            .orElseThrow(() ->
                refuse(
                    Reason.RACED_BY_ANOTHER_WRITER,
                    "membership " +
                    pending.getId() +
                    " stopped being PENDING between reading it and writing it, so acknowledgement " +
                    eventId +
                    " would have overwritten a decision somebody else took"
                )
            );

        record(event, patientId, activated, planCode);
        log.info(
            "Applied plan acknowledgement {}: membership {} for patient {} on plan {} is now ACTIVE",
            eventId,
            activated.getId(),
            patientId,
            planCode
        );
    }

    /**
     * The patient this acknowledgement is about.
     *
     * <p>Lower-cased here rather than trusted: the contract says the subject is lower-cased as every frame on
     * {@code patient-events} is, and a producer that has not been written yet cannot be relied on to have remembered.
     * The lookup is case-insensitive regardless; this is so that what is logged is what would have been keyed on.</p>
     */
    private String subjectEmail(PatientEvent event) {
        String email = event.subject() == null ? null : event.subject().email();
        if (isBlank(email)) {
            throw refuse(
                Reason.NO_SUBJECT_KEY,
                "acknowledgement " +
                event.eventId() +
                " carries no subject.email; every frame in this estate is keyed on the lower-cased email and one without it names nobody"
            );
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Refuses a frame naming a status this service cannot map, or one it can map but this exchange does not carry.
     *
     * <p><b>The payload contract is {@code { Plan }} and carries no status at all</b>, so the common case is that
     * there is nothing here to check and the acknowledgement means what the contract says it means: activate. This
     * exists for the disagreement already on the record — hc-admin's item 54 still describes this repo's vocabulary
     * as six values including {@code VERIFIED}, and this repo shipped five. A console built from their entry would
     * offer an administrator a decision {@link MembershipStatus} has no constant for, and the failure would land here,
     * on a frame they sent, which is the most expensive place in the estate to diagnose a vocabulary disagreement.
     * Naming the value and the five it could have been costs one log line and moves the diagnosis to where the
     * question is.</p>
     */
    private void assertActivating(String eventId, Map<String, Object> data) {
        Object raw = data == null ? null : data.get("status");
        if (raw == null || isBlank(String.valueOf(raw))) {
            return;
        }
        String named = String.valueOf(raw);
        MembershipStatus status = MembershipStatus
            .from(named)
            .orElseThrow(() ->
                refuse(
                    Reason.STATUS_NOT_IN_THIS_SERVICES_VOCABULARY,
                    "acknowledgement " +
                    eventId +
                    " names status '" +
                    named +
                    "', which is not one of " +
                    List.of(MembershipStatus.values()) +
                    " — note that VERIFIED was ruled out on 2026-09-08 and an approval sets ACTIVE directly"
                )
            );
        if (status != MembershipStatus.ACTIVE) {
            throw refuse(
                Reason.STATUS_NOT_AN_ACTIVATION,
                "acknowledgement " +
                eventId +
                " names status " +
                status +
                ", and this exchange carries PENDING → ACTIVE only; anything else would be a larger grant than the contract"
            );
        }
    }

    /**
     * The one membership this acknowledgement applies to.
     *
     * <p><b>An acknowledgement keyed on an email names a patient, not a membership</b>, so this repo decides which one
     * it is about. Item 19 decided: the patient's single {@code PENDING} membership, refusing rather than guessing if
     * there is not exactly one. Silently picking one of two pending memberships activates a year of care nobody sold
     * — the same class as the self-chosen {@code renewalDate} item 18's review stripped. Refusal is loud and
     * recoverable; a wrong pick is neither.</p>
     */
    private Membership singlePendingMembership(String eventId, String email, String patientId) {
        List<Membership> pending = membershipService.pendingFor(patientId);
        if (pending.isEmpty()) {
            throw refuse(
                Reason.NO_PENDING_MEMBERSHIP,
                "acknowledgement " + eventId + " names " + email + ", who holds no PENDING membership for it to be about"
            );
        }
        if (pending.size() > 1) {
            throw refuse(
                Reason.MORE_THAN_ONE_PENDING_MEMBERSHIP,
                "acknowledgement " +
                eventId +
                " names " +
                email +
                ", who holds " +
                pending.size() +
                " PENDING memberships (" +
                pending.stream().map(Membership::getId).toList() +
                "); refusing rather than choosing one"
            );
        }
        return pending.getFirst();
    }

    /**
     * Refuses an acknowledgement naming a plan the membership does not hold.
     *
     * <p><b>This is the whole reason {@code plan} is in a one-field payload.</b> It is a value this service wrote
     * itself and hc-admin echoes back, so it carries no new information — its use is exactly this refusal, which
     * turns a silent mismatch into a stop. Said here because a later reader who does not know that deletes the check
     * as redundant.</p>
     *
     * <p>Compared case-insensitively and trimmed. A difference of casing is a difference of spelling rather than of
     * meaning, and refusing on one would be a refusal that taught nobody anything.</p>
     */
    private void assertPlanAgrees(String eventId, String email, String planCode, Membership pending) {
        String held = pending.getPlan();
        if (held == null || !held.trim().equalsIgnoreCase(planCode)) {
            throw refuse(
                Reason.PLAN_DISAGREES,
                "acknowledgement " +
                eventId +
                " for " +
                email +
                " names plan '" +
                planCode +
                "' but membership " +
                pending.getId() +
                " holds '" +
                held +
                "'"
            );
        }
    }

    /**
     * The plan an acknowledgement names, or null.
     *
     * <p><b>Two key spellings and two value shapes are accepted, and that tolerance is temporary.</b> The record
     * describes this field twice and not identically: item 18 defines {@code Plan} as a commercial object with a
     * {@code code}, while item 19 says what comes back is <em>"the {@code planCode} this repo sent"</em>, which is the
     * flat string this service publishes. hc-admin has written neither, so there is nothing to measure against and a
     * guess that refused every frame would be the worst of the available outcomes. Reading both costs eight lines and
     * refuses on the thing that actually matters, which is a plan that disagrees.</p>
     *
     * <p><b>Collapse this to one spelling when their item 54 lands</b>, and record which they chose. A payload reader
     * that accepts everything is a contract that has stopped being one.</p>
     */
    private static String planCodeIn(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object value = data.containsKey("plan") ? data.get("plan") : data.get("planCode");
        if (value instanceof Map<?, ?> plan) {
            value = plan.get("code");
        }
        if (value instanceof String named && !named.isBlank()) {
            return named.trim();
        }
        return null;
    }

    /**
     * Records that this frame was applied.
     *
     * <p>After the membership, never before — see the class javadoc. {@code insert} rather than {@code save} so that a
     * duplicate key is an error rather than an overwrite; the {@code existsById} above should already have caught it,
     * and the two disagreeing would mean two consumers on one partition, which is worth knowing about rather than
     * papering over.</p>
     */
    private void record(PatientEvent event, String patientId, Membership activated, String planCode) {
        try {
            planVerifications.insert(
                new PlanVerification()
                    .id(event.eventId())
                    .type(event.type())
                    .occurredAt(event.occurredAt())
                    .appliedAt(Instant.now())
                    .patientId(patientId)
                    .membershipId(activated.getId())
                    .planCode(planCode)
            );
        } catch (DuplicateKeyException e) {
            // Two deliveries of one frame overlapping, which one consumer per partition should make impossible. The
            // membership write above was the conditional update, so the second of them changed nothing.
            log.warn("Plan acknowledgement {} was recorded twice — check that only one consumer holds this partition", event.eventId(), e);
        }
    }

    /**
     * Logs the refusal and builds it.
     *
     * <p>Logged here as well as thrown, because what the container logs is a stack trace ending in <em>"Retry policy
     * … exhausted"</em>; the line an operator needs names the event, the patient and what disagreed with what.</p>
     *
     * <p><b>Expect four of these per refused frame, not one.</b> {@code maxAttempts: 3} counts retries after the first
     * delivery, so the refusal is logged once and then three times more over about eight seconds before the frame is
     * dead-lettered. It is set explicitly in {@code application.yml} so that repetition is a known number rather than
     * a surprising one — a reader who sees the same event id four times should not go looking for four events.</p>
     */
    private PlanVerificationRefusedException refuse(Reason reason, String message) {
        log.warn("Refusing a plan acknowledgement [{}]: {}", reason, message);
        return new PlanVerificationRefusedException(reason, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
