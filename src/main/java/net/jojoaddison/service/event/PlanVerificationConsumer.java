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
 * <h2>The far end is built, and this class was written against their code rather than the backlog</h2>
 *
 * <p><b>hc-admin's item 54 landed on their {@code main} at {@code ceb9eae}, "Item 54: an admin verifies a plan choice
 * and hc-patient is told."</b> Everything below that names their behaviour was read from
 * {@code PlanVerificationEvent} and {@code PatientPlanVerificationService} on 2026-09-10, not from their entry — they
 * built their half by reading this file, and this paragraph closes the loop in the other direction. What their entry
 * still records and their code contradicts is noted where it matters.</p>
 *
 * <p>So three things that were assumptions here are now measured. They send <b>this repo's {@link PatientEvent}
 * envelope</b>, not the flat shape their only other outbound DTO uses; they set {@code subject.email} <b>lowercased
 * at the publish point</b>; and they set the Kafka <b>partition key</b> to the same address, through a four-argument
 * publish written for this exchange. No fallback to the record key is needed, and one would have been dead code:
 * their earlier publishers set no key expression at all.</p>
 *
 * <p><b>It dispatches on the event type, which reverses this class's first decision.</b> That decision — apply every
 * frame, because the type string was never agreed — was right while their half was unbuilt: guessing a literal would
 * have reproduced item 18's fortnight-long half-loop inverted, with this side consuming and ignoring while the loop
 * read as working. It has stopped being right, because the literal is no longer a guess. {@code PlanVerified} is a
 * constant on both sides now, and the type each frame carries is still recorded on {@link PlanVerification} — which
 * is what let the question be answered from data, as intended.</p>
 *
 * <p>Keeping the old behaviour would have been the sharper hazard of the two. A rejection or correction frame
 * carrying a matching {@code plan} and no {@code status} <b>activated the membership</b>: {@link #assertActivating}
 * only fires when a status is present, and the settled payload has none. Their item 54 says a second control follows
 * the moment a rejection path is decided, so that frame is coming. See
 * {@link #assertTypeIsTheOneThisTopicCarries} for why it is refused rather than ignored.</p>
 *
 * <h2>Refusing, and what reaches the dead-letter queue</h2>
 *
 * <p>Twelve ways an acknowledgement can be wrong, enumerated in {@link Reason} and each refused separately so the log
 * says which. <b>Every refusal throws</b>, which dead-letters the frame with its bytes intact and advances the
 * offset, where a swallowed one would leave only a log line nothing alerts on. The binding is never at risk — the
 * binder retries, dead-letters and takes the next frame, which {@code PlanVerificationRoundTripIT} proves against a
 * real broker rather than asserting.</p>
 *
 * <p><b>"Recoverable" is worth stating precisely, because the word flatters what it describes.</b> A dead-lettered
 * frame is recoverable only by a person reading {@code patient-events-plan.hc-patient-dlq} and replaying it after
 * fixing whatever caused the refusal. Nothing here retries it and nothing alerts on it. That is still better than a
 * log line — the bytes survive and the decision can be reapplied — but it is a human process, not a mechanism.</p>
 *
 * <p><b>Two things are ignored rather than refused, and keeping the dead-letter queue meaningful depends on both.</b>
 * A frame this service has already applied, identified by its event id: at-least-once delivery working as specified.
 * And a frame asking for a state that already holds — see {@link #alreadySatisfied}, which exists because hc-admin
 * republishes with a fresh event id on purpose, so their recovery mechanism would otherwise look like our failure.
 * Dead-lettering either would fill the queue with successes, and an operator reading a queue of successes stops
 * reading it.</p>
 *
 * <h2>Idempotency</h2>
 *
 * <p>{@link PatientEvent#eventId()} keys {@link PlanVerification}, whose {@code _id} it is — so "have I applied this
 * frame" is a primary-key read, and a replay produces neither a second write nor a second announcement. The ledger is
 * written <b>after</b> the membership, deliberately: writing it first would let a crash in the gap lose the
 * verification silently, and this file chooses the visible failure every time it has the choice.</p>
 *
 * <p><b>Two protections, and they overlap almost everywhere.</b> {@link #alreadySatisfied} catches most of what the
 * ledger catches, because a replay usually asks for a state that already holds. <b>They separate in exactly one
 * place, and it is the one that matters:</b> when the patient has since chosen the same tier again, a redelivery
 * finds a fresh {@code PENDING} membership whose plan agrees — {@code alreadySatisfied} is never consulted, because
 * the pending check runs first — and only the event id stops it being activated by an acknowledgement about a
 * different membership. That is what {@code PlanVerificationRoundTripIT} mutates against; the overlap is why two
 * earlier versions of that test passed with the guard deleted.</p>
 *
 * <p><b>So a lost ledger row is not harmless, and the sentence that used to say it "costs nothing in the ordinary
 * case" was wrong in the direction that matters.</b> The ordinary case is fine; the one case the ledger uniquely
 * covers is exactly the one a lost row re-opens. {@link #record} swallows a failed insert anyway — dead-lettering a
 * frame that has already written and announced is the worse of the two — but it logs at {@code ERROR} rather than
 * {@code WARN}, and the reasoning for the trade is written out there rather than implied here.</p>
 *
 * <p><b>The ledger is patient data and is erased with the patient</b> — {@code PatientErasureService.PATIENT_SCOPED}
 * names it, and it was missed there for one review. So idempotency is bounded by the patient's existence: a
 * redelivery arriving after an erasure is no longer recognised as a replay and refuses
 * {@link Reason#UNKNOWN_PATIENT} instead. Loud, correct, and not a regression — see {@link PlanVerification}.</p>
 *
 * <h2>It no longer idles, and nothing end to end has been observed either</h2>
 *
 * <p>Item 19 built this half first on the argument that a consumer nobody publishes to idles harmlessly and drains
 * the backlog the day the producer starts — the opposite of item 18's publish-first, which spent a fortnight as a
 * half-loop. <b>That day arrived while this was in review.</b> The consumer group reads from {@code earliest}, which
 * is what makes "the backlog drains into it" true rather than hopeful; it is set explicitly in
 * {@code application.yml} rather than inherited, because it is the whole justification for the build order.</p>
 *
 * <p><b>Both halves existing is not the same as the loop having been watched close.</b> Nothing here has been run
 * against their stack; every claim about their behaviour is read off their source. The proof that remains is two
 * services running together on the quality stacks, and it is item 19's last open bullet. Do not add a producer to
 * this repository to stand in for it — the only things that write to this topic are hc-admin and, in tests, a raw
 * Kafka producer inside the test.</p>
 */
@Component
public class PlanVerificationConsumer {

    /**
     * The one event type this topic carries.
     *
     * <p>A cross-repo contract held as a literal on both sides: hc-admin's {@code PlanVerificationEvent.TYPE}. A
     * rename here is not a compile error there, so renaming it means changing both repositories and both sides'
     * tests in the same breath — the same rule {@code PatientEventType.PLAN_CHOSEN} carries for the other direction.</p>
     */
    public static final String PLAN_VERIFIED = "PlanVerified";

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
     * <p><b>The replay check comes first, then the frame's own shape, then the patient, then the membership.</b> The
     * ledger read leads because a redelivery is the ordinary case on an at-least-once topic and must be cheap to
     * dismiss — it is not that every database read comes last, and saying so would misdescribe the order. Everything
     * after it is arranged so that a producer defect is reported as a producer defect whether or not the patient it
     * names happens to exist here.</p>
     */
    void apply(PatientEvent event) {
        if (event == null) {
            // The binder does not hand a function a null payload, so this is defensive rather than expected. Refused
            // rather than returned, because a silent return would be indistinguishable from a frame applied — and
            // under its own reason, because NO_EVENT_ID would send the next reader to hc-admin's serialiser.
            throw refuse(Reason.NO_FRAME, "a null frame arrived on patient-events-plan");
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

        assertTypeIsTheOneThisTopicCarries(event);

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

        Optional<Membership> chosen = singlePendingMembership(eventId, email, patientId, planCode);
        if (chosen.isEmpty()) {
            // Already in the state this frame asks for. Not applied, not refused, not dead-lettered — see
            // alreadySatisfied for why hc-admin's design makes this an ordinary event rather than an edge case.
            return;
        }
        Membership pending = chosen.orElseThrow();
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
    private Optional<Membership> singlePendingMembership(String eventId, String email, String patientId, String planCode) {
        List<Membership> pending = membershipService.pendingFor(patientId);
        if (pending.isEmpty()) {
            if (alreadySatisfied(patientId, planCode)) {
                log.info(
                    "Ignoring plan acknowledgement {} for {} — plan {} is already ACTIVE, so this asks for a state that holds",
                    eventId,
                    email,
                    planCode
                );
                return Optional.empty();
            }
            throw refuse(
                Reason.NO_PENDING_MEMBERSHIP,
                "acknowledgement " +
                eventId +
                " names " +
                email +
                ", who holds no PENDING membership for it to be about and nothing ACTIVE on plan " +
                planCode
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
        return Optional.of(pending.getFirst());
    }

    /**
     * Whether this acknowledgement asks for a state that already holds.
     *
     * <h2>Why a "no pending membership" frame is often not an error</h2>
     *
     * <p><b>hc-admin mints a fresh {@code eventId} for every press of their verify button, deliberately</b>, and
     * republishing is how they recover a frame they think was lost: <em>"the echo is the acknowledgement, and
     * republishing unconditionally is what makes a lost frame recoverable"</em>, with <em>"two presses of the button
     * must be two ids or the second is silently ignored as a redelivery."</em> Read from their
     * {@code PatientPlanVerificationService} on 2026-09-10, not from their backlog.</p>
     *
     * <p>So the ledger cannot suppress a repeat — by their design it must not — and without this check the second
     * press, and <em>every successful recovery-republish</em>, would be refused and dead-lettered. That would fill
     * the dead-letter queue with frames whose only fault is that they worked, which is exactly what this class's
     * javadoc promises the queue does not hold. Their recovery mechanism would look like our failure.</p>
     *
     * <p><b>It cannot swallow a real verification, because the pending check runs first.</b> A patient who re-chooses
     * the same tier has a {@code PENDING} membership, so the caller never reaches here; only when there is nothing
     * pending at all does "is it already done" get asked. And it is asked <em>on the plan named</em>: something
     * {@code ACTIVE} on a different tier leaves the refusal in place, because that frame really is about a membership
     * this service cannot find.</p>
     */
    private boolean alreadySatisfied(String patientId, String planCode) {
        return membershipService.activeFor(patientId).stream().anyMatch(active -> planMatches(active.getPlan(), planCode));
    }

    /**
     * Refuses a frame that is not the one type this topic carries.
     *
     * <p>Written as a literal because it is a cross-repo contract, and no longer a guess: hc-admin's
     * {@code PlanVerificationEvent.TYPE} is {@code "PlanVerified"}, pinned on their side as a constant and read from
     * their {@code main} at {@code ceb9eae} on 2026-09-10. Their own javadoc nominates it — <em>"it is what they
     * should pin when they add dispatch"</em>. Both ends now assert it, which is the only thing that makes a rename a
     * two-repository change rather than a silent one.</p>
     *
     * <p><b>Refused rather than ignored, which inverts the estate's usual rule on purpose.</b> That rule — meet a type
     * you do not know and skip it — is written for {@code patient-events}, a shared topic where new types arrive
     * routinely and skipping one costs nothing. This topic carries one exchange between two products, and the frame
     * most likely to appear under a different name is a <em>rejection</em>: their item 54 says a second control
     * follows the moment a rejection path is decided. Ignoring that would be the half-loop this repo keeps
     * rediscovering; applying it — which is what happened before this check existed, since the settled payload
     * carries no {@code status} for {@link #assertActivating} to catch — would activate a membership an administrator
     * had refused. The dead-letter queue holds it instead, until somebody models it.</p>
     */
    private void assertTypeIsTheOneThisTopicCarries(PatientEvent event) {
        if (!PLAN_VERIFIED.equals(event.type())) {
            throw refuse(
                Reason.UNEXPECTED_TYPE,
                "acknowledgement " +
                event.eventId() +
                " is of type '" +
                event.type() +
                "' and this topic carries '" +
                PLAN_VERIFIED +
                "' only; a second type is a contract change and is held rather than guessed at"
            );
        }
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
        if (!planMatches(held, planCode)) {
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
     * <p><b>{@code plan}, a flat string — and that is now their choice rather than this repo's reading.</b> This
     * accepted two key spellings and two value shapes while hc-admin's half was unbuilt, because the record described
     * the field twice and not identically: item 18 defines {@code Plan} as a commercial object with a {@code code},
     * item 19 says what comes back is <em>"the {@code planCode} this repo sent"</em>. Their
     * {@code PlanVerificationEvent.PlanData} settles it — <em>"the key is {@code plan} and the value is a flat
     * string"</em>, serialising to {@code {"plan":"MELON"}} — so the object-with-a-{@code code} branch is gone, as
     * this method's own javadoc undertook to do once they chose.</p>
     *
     * <p><b>{@code planCode} is kept as an alias and demoted to exactly that.</b> It is this service's own outbound
     * spelling on {@code PlanChosen}, which their earlier draft echoed; keeping it costs one line and turns a
     * plausible refactor on their side into a non-event. It is no longer an open question.</p>
     *
     * <p><b>A present-but-null key is not an absent one.</b> This chose between the two spellings with
     * {@code containsKey}, so {@code {"plan": null, "planCode": "PAWPAW"}} refused {@code NO_PLAN_NAMED} with a
     * perfectly good code unread in the same map — and <b>null-valued keys are hc-admin's house style</b>: item 27's
     * closing note records them putting a null {@code planCode} straight on the wire, and their {@code setOrUnset}
     * reads absent and null identically. Falling through on null rather than on absence removes a refusal that would
     * have read as a contract disagreement when it was a serialisation habit.</p>
     */
    private static String planCodeIn(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object value = data.get("plan") != null ? data.get("plan") : data.get("planCode");
        if (value instanceof String named && !named.isBlank()) {
            return named.trim();
        }
        return null;
    }

    /**
     * Whether two plan codes name the same tier.
     *
     * <p>Trimmed and case-insensitive, for the reason {@link #assertPlanAgrees} gives: a difference of casing is a
     * difference of spelling rather than of meaning. Shared so that the refusal and the already-satisfied check
     * cannot drift into comparing the same values by different rules — hc-admin's javadoc pins their side to
     * {@code equalsIgnoreCase} against this exact field.</p>
     */
    private static boolean planMatches(String held, String named) {
        return held != null && named != null && held.trim().equalsIgnoreCase(named.trim());
    }

    /**
     * Records that this frame was applied.
     *
     * <p>After the membership, never before — see the class javadoc. {@code insert} rather than {@code save} so that a
     * duplicate key is an error rather than a silent overwrite.</p>
     *
     * <h2>Why the catch is broad, and why it is not dead code even though nothing reaches it</h2>
     *
     * <p><b>Nothing reaches it on any path this class has today.</b> A second delivery finds the ledger row and
     * returns at {@code existsById}; if it somehow got past that, the membership is no longer {@code PENDING}, so the
     * conditional update matches nothing and the caller refuses before arriving here. That the conditional update
     * lets exactly one frame through is the same property that makes idempotency structural rather than incidental.</p>
     *
     * <p><b>It was {@code catch (DuplicateKeyException)} and that was proved insufficient by mutation, which is the
     * only reason this paragraph exists.</b> Deleting the {@code existsById} guard to check the replay test could see
     * it made this line reachable, and what came out was <em>not</em> a {@code DuplicateKeyException}: the throw went
     * straight past the catch, the frame dead-lettered, and the membership stayed activated and announced. So the
     * narrow catch was documented as a safety net against a half-apply while not actually catching the one throw that
     * causes it. That is the shape this repository keeps recording — <em>a javadoc asserting a guard that does not
     * hold</em> — and it survived here precisely because the code was unreachable and therefore untested.</p>
     *
     * <p><b>Broad is right rather than lazy at this point in the method.</b> By the time this runs the membership is
     * written and hc-admin has been told; nothing that fails here may undo either, so the ledger write is
     * best-effort in exactly the way {@link PatientEventPublisher} is about publishing — <em>"the write already
     * happened"</em>. Letting anything propagate would dead-letter a frame that has been applied, which is the one
     * half-apply this class cannot undo and the one whose replay would do damage.</p>
     *
     * <p><b>Swallowing is not free, and the earlier version of this paragraph said it was.</b> It claimed the loss
     * cost nothing because {@link #alreadySatisfied} would ignore the later redelivery anyway — "the two protections
     * overlap here, which is what makes swallowing safe". That contradicts the class javadoc, and the class javadoc
     * is the correct one: the protections <b>separate in exactly one place</b>, and a lost ledger row is precisely
     * the condition that re-opens it. If the patient chooses the same tier again, a redelivery finds a fresh
     * {@code PENDING} membership whose plan agrees, {@code alreadySatisfied} is never consulted because the pending
     * check runs first, and the only thing that would have stopped that membership being activated by an
     * acknowledgement about a different one was the row that was just lost. That is verbatim the hazard
     * {@code PlanVerificationRoundTripIT} was rewritten to catch.</p>
     *
     * <p><b>So this is a trade, and it is the one being chosen deliberately:</b> a dead-lettered frame that has
     * already written and announced, against a re-opened re-selection window that needs a Mongo failure <em>and</em>
     * a later duplicate delivery <em>and</em> the patient re-choosing the same tier before it does any harm. The
     * first is certain whenever the insert fails; the second needs three things to coincide. Hence
     * {@code log.error} rather than {@code log.warn} — a lost row is the one condition under which a duplicate
     * delivery can activate a membership nobody verified, and it is the line an operator should be able to find.</p>
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
        } catch (RuntimeException e) {
            // Deliberately swallowed, and deliberately broad — see the javadoc for both halves.
            //
            // ERROR rather than WARN: the membership is fine, but the idempotency key for this frame is gone, and
            // that is the one condition under which a duplicate delivery can activate a membership nobody verified.
            log.error(
                "Plan acknowledgement {} was applied but its ledger row was NOT written — the membership is correct, " +
                "but a redelivery of this frame is no longer recognised, so a later choice of the same plan could be " +
                "activated by it. Check membership {} for patient {}.",
                event.eventId(),
                activated.getId(),
                patientId,
                e
            );
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
