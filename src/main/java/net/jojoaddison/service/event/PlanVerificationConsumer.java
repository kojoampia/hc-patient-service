package net.jojoaddison.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
 * The one consumer bound to {@code admin.event} — hc-admin's own channel — which reads the frames addressed to this
 * service and applies an administrator's plan verification by moving that patient's pending membership to
 * {@code ACTIVE}.
 *
 * <h2>The only channel this service consumes that it does not own</h2>
 *
 * <p>Everything else here <em>publishes</em>: {@code patient-events} and {@code patient.event} are this subsystem's own
 * streams and every producer on them is ours. This is the return leg of the exchange item 18 opened — a patient chooses
 * a tier, this service writes a {@code PENDING} {@link Membership} and announces it, an administrator decides on it next
 * door, and the decision comes back here. Backlog item 19 built it on {@code patient-events-plan}; <b>backlog item 47
 * moved it to {@code admin.event} and removed that binding</b>, under the estate decision of 2026-09-17: one channel per
 * product carrying everything that product has to say, so a new consumer subscribes rather than negotiating a topic.</p>
 *
 * <pre>
 * channel   admin.event                  hc-admin publishes, three products consume
 * type      PlanVerified                 one of three types on the channel; see AdminEvent
 * subject   ("DirectoryLink", linkId)    THE RECORD, NOT THE PERSON — the addressee is in data
 * payload   { plan, subjectKey }         the decided tier, and the lower-cased email to apply it to
 * key       DirectoryLink/&lt;linkId&gt;       and no patientKey header, unlike patient-events
 * </pre>
 *
 * <h2>⚠ The subject moved, and reading {@code subject.email} here would refuse every real verification</h2>
 *
 * <p><b>This is the one thing about item 47 that nothing but hc-admin's source could have told us.</b> The frame they
 * publish on {@code patient-events-plan} is this repo's own {@link PatientEvent} envelope, with the address in
 * {@code subject.email} and {@code data} of exactly {@code {"plan": "…"}} — item 19 was written against it and it is
 * still correct <em>there</em>. The frame they publish on {@code admin.event} is a <b>different class</b>
 * ({@code PlanVerifiedEvent}, not {@code PlanVerificationEvent}) in <b>the channel's own envelope</b>: {@code subject}
 * is {@code (entityType, entityId)} like every other frame on it, and the address travels as
 * <b>{@code data.subjectKey}</b>. Their reasoning is the architect's D1 of 2026-09-25 — one channel, one meaning for
 * {@code subject} — and {@link AdminEvent} carries it.</p>
 *
 * <p>A reader ported across by changing the destination alone would therefore refuse {@link Reason#NO_SUBJECT_KEY} on
 * every genuine verification, dead-letter it, and stay green in every test written from the old shape. Both repositories
 * have already paid for the general form of this once: <em>read the far end's code, not the specification.</em> Their
 * {@code AdminEntityEvent} javadoc says so about us, and it is the reason this class was rewritten against their
 * {@code main} at {@code 7deda9a} and against 7435 frames read off the live quality broker on 2026-09-25, rather than
 * against item 47's prose — which describes the old envelope and was written before theirs existed.</p>
 *
 * <h2>A type that is not ours is the ORDINARY case now, and is ignored rather than refused</h2>
 *
 * <p><b>This inverts item 19's decision, and the inversion is the whole of item 47's behaviour change.</b>
 * {@code patient-events-plan} carried one exchange between two products, so a frame of another type there was a contract
 * change and refusing it — keeping the bytes in the dead-letter queue until somebody modelled it — was right.
 * {@code admin.event} carries <em>everything hc-admin has to say</em>: measured on 2026-09-25, <b>7433 of its 7435
 * frames were {@code EntityChanged}</b>, hc-admin's entity CRUD, of which this product has no use for a single one.
 * Refusing an unexpected type on this channel would dead-letter the channel — four handlings and a dead letter per
 * frame, for ever, growing with hc-admin's write rate.</p>
 *
 * <p><b>So {@link #apply} dispatches on {@code type} first, before every other check including the ledger read.</b> That
 * ordering is load-bearing rather than tidy: an {@code EntityChanged} frame has a perfectly good {@code eventId}, so it
 * sails past the blank-id guard, and under item 19's ordering it would have reached the type check and died there —
 * 7433 times and counting. A frame that is not ours costs one string comparison and no database read at all.</p>
 *
 * <p><b>What that gives up, said plainly: a rejection frame is now ignored instead of held.</b> hc-admin's item 54
 * promises a second control the moment a rejection path is decided, and this class used to dead-letter it so a person
 * would find it. It will now be ignored — and the state that leaves behind is the safe one, because the membership stays
 * {@code PENDING} and nothing is granted; the frame also stays on hc-admin's channel under its retention, where it can be
 * replayed once somebody models it. The alternative — enumerate the types we know and refuse the rest — was rejected
 * because hc-admin adding a type is not an event they tell us about. That is the point of one channel, and a consumer
 * that dead-letters everything it has not been introduced to makes every new entity in their service an outage in ours.
 * {@link #ignoredFrames} is what keeps the ignoring visible; see {@link #ignore}.</p>
 *
 * <p><b>⚠ And it gives up a second thing, which is less obvious and worse: the alarm moved.</b> {@link #PLAN_VERIFIED}
 * is a literal pinned against a string another repository owns, so if it ever diverges — a rename, a casing change, an
 * envelope of theirs that stops carrying {@code type} — <b>every</b> verification takes the ignore path. Nothing is
 * refused, nothing is dead-lettered, nothing logs above {@code TRACE}, memberships stay {@code PENDING}, and the
 * ignored count goes on climbing on their entity churn exactly as it does when all is well. Under item 19's rule that
 * same drift dead-lettered loudly. The ignore is still right for a shared channel; what it needs is a different
 * signal, and that is {@link #appliedFrames} — a rate that goes to zero against a live channel, which is a shape the
 * ignored count alone cannot show.</p>
 *
 * <p><b>The method name IS the binding name.</b> Spring Cloud Stream derives {@code adminEventConsumer-in-0} from the
 * {@code @Bean} method, and {@code spring.cloud.function.definition} names it in YAML — so renaming the method does not
 * fail to compile and does not fail to start. The context comes up, every request is served, and every decision hc-admin
 * sends is dropped on the floor with nothing thrown and nothing logged at a level anybody watches.
 * {@code PlanVerificationConsumerBindingIT} is the only thing that can see that, and item 32 is what a binding bound to
 * nothing cost this repository: reviewed five times, deployed, healthy, serving, and consuming a topic that did not
 * exist.</p>
 *
 * <p><b>This class keeps its name although the binding no longer does, and that is not an oversight.</b> It is named for
 * what it does, which has not changed; and a class called {@code AdminEventConsumer} would make Spring derive the bean
 * name {@code adminEventConsumer} for the component itself, so the {@code @Bean} method below would be a factory-bean
 * reference pointing at its own class. The context refuses to start — {@link MembershipStreamConsumer} and the gateway's
 * {@code PatientEventMailRouter} both record the same trap.</p>
 *
 * <h2>The far end is built, and this class was written against their code rather than the backlog</h2>
 *
 * <p><b>hc-admin's item 145 step 1 landed on their {@code main} at {@code 7deda9a}, "both return legs also publish on
 * admin.event", and their image is already running on the quality box.</b> Everything here that names their behaviour
 * was read from {@code PlanVerifiedEvent}, {@code PatientPlanVerificationService}, {@code AdminChannel} and
 * {@code OutboundEventPublisher} on 2026-09-25, and then checked against frames on the live broker — not from either
 * product's backlog entry. Item 19 did the same thing against their item 54 ({@code ceb9eae}) and was right about the
 * topic it was written for; the entry describing <em>this</em> migration predates their envelope and describes the old
 * one, which is exactly why the code was read instead.</p>
 *
 * <p><b>Their publish is additive and ours is a move, and the order is deliberate.</b> They still publish the old frame
 * on {@code patient-events-plan} as well, and their {@code PlanVerifiedEvent} javadoc explains why they will not stop
 * until this consumer's lag on the old topic is zero and its binding is gone: <em>remove the consumer before the
 * producer</em> — a producer writing where nobody reads is harmless, a consumer reading where nobody writes is silence
 * that looks like health. This commit is the consumer half. <b>Their lag was zero when it was written</b> (group
 * {@code hc-patient-service}, {@code patient-events-plan}, current offset 4 of 4, measured 2026-09-25), so nothing on
 * that topic is abandoned undelivered.</p>
 *
 * <p><b>Three things that were assumptions in item 19 and are now measured, on the new channel.</b> They mint a
 * <em>second</em> {@code eventId} for this frame rather than reusing the one on the old topic — "two channels, two
 * identities" — so the two frames about one decision are two ledger rows if both are ever applied, and the ledger is not
 * a defence against reading both channels at once. They lowercase and trim {@code data.subjectKey} at the publish point,
 * and a test on their side pins it, because a wrong-cased join key is item 26's silent failure. And they key the
 * partition on {@code DirectoryLink/<linkId>} with <b>no {@code patientKey} header</b>, so nothing about the Kafka key
 * addresses a patient any more: the addressee is in the payload, which is where this class reads it.</p>
 *
 * <p><b>It dispatches on the event type, which reverses this class's first decision and then reverses what that
 * reversal did with the frames it did not recognise.</b> The original decision — apply every frame, because the type
 * string was never agreed — was right while their half was unbuilt. Item 19 replaced it with a refusal, which was right
 * for a topic carrying one exchange. Item 47 keeps the dispatch and replaces the refusal with an ignore, because the
 * channel now carries everything hc-admin has to say; see the section above, and {@link #ignore}. The type each applied
 * frame carried is still recorded on {@link PlanVerification}, which is what let the earlier question be answered from
 * data rather than from a comment.</p>
 *
 * <p>Applying an unrecognised frame would still be the sharpest hazard of the three, and it is worth keeping the reason
 * visible. A rejection or correction frame carrying a matching {@code plan} and no {@code status} would <b>activate the
 * membership</b>: {@link #assertActivating} only fires when a status is present, and the settled payload has none. So
 * the choice was never between refusing and applying — it is between refusing and ignoring, and only one of those
 * scales to a channel this service does not own.</p>
 *
 * <h2>Refusing, and what reaches the dead-letter queue</h2>
 *
 * <p>Eleven ways a verification addressed to this service can be wrong, enumerated in {@link Reason} and each refused
 * separately so the log says which. <b>Every refusal throws</b>, which dead-letters the frame with its bytes intact and
 * advances the offset, where a swallowed one would leave only a log line nothing alerts on. The binding is never at risk
 * — the binder retries, dead-letters and takes the next frame, which {@code PlanVerificationRoundTripIT} proves against
 * a real broker rather than asserting.</p>
 *
 * <p><b>Only a frame that binds to {@link AdminEvent} <em>and</em> carries this service's own type can reach a refusal
 * in this class</b>, which is what keeps the dead-letter queue readable now that the channel is shared: hc-admin's
 * entity churn is ignored and never refused, so it does not fill the queue.</p>
 *
 * <p><b>⚠ That is not the same as "the queue holds nothing but our own type", and an earlier version of this
 * paragraph claimed exactly that.</b> It said the queue holds plan verifications and nothing else, which this class's
 * own tests falsify twice over:</p>
 *
 * <ul>
 *   <li><b>Conversion runs before dispatch.</b> A frame that will not bind to {@link AdminEvent} is dead-lettered by
 *       the binder, which has never seen {@link #PLAN_VERIFIED} and cannot — {@code apply} is not called at all.
 *       {@code PlanVerificationRoundTripIT.aPoisonFrameIsDeadLetteredAndTheNextGoodFrameIsStillConsumed} puts one
 *       there by construction, and any future frame of hc-admin's that this record cannot bind lands there too,
 *       whatever its type. {@link AdminEvent} is as tolerant as it is precisely to keep that set small.</li>
 *   <li><b>The null-frame guard precedes the type check</b>, necessarily — a null frame has no type to read — so a
 *       tombstone refuses {@link Reason#NO_FRAME} whether or not it was ever addressed here.</li>
 * </ul>
 *
 * <p>The distinction matters to whoever reads the queue: it is <em>mostly</em> ours, and a frame in it that is not a
 * plan verification is a conversion failure or a tombstone rather than evidence that the type dispatch leaked.</p>
 *
 * <p><b>"Recoverable" is worth stating precisely, because the word flatters what it describes.</b> A dead-lettered
 * frame is recoverable only by a person reading {@code admin.event.hc-patient-dlq} and replaying it after fixing
 * whatever caused the refusal. Nothing here retries it and nothing alerts on it. That is still better than a log line —
 * the bytes survive and the decision can be reapplied — but it is a human process, not a mechanism.</p>
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
 * <p>{@link AdminEvent#eventId()} keys {@link PlanVerification}, whose {@code _id} it is — so "have I applied this
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
 * <h2>The first start replays the whole channel, and that is chosen rather than tolerated</h2>
 *
 * <p>The group keeps reading from {@code earliest}, set explicitly in {@code application.yml}. Kafka scopes offsets per
 * {@code (group, topic)}, so {@code hc-patient-service} arrives on {@code admin.event} with no committed offset and
 * <b>reads the channel from the beginning exactly once</b>: 7435 frames as of 2026-09-25, of which 7433 cost a string
 * comparison each. {@code resetOffsets} stays at its default of false, which is the half that matters afterwards —
 * {@code startOffset} applies only while the group has no committed offset, so a restart does not do this again.</p>
 *
 * <p><b>{@code latest} was the alternative and it is the one that loses a decision.</b> hc-admin has been publishing
 * here since their roll; a verification pressed between their deploy and ours sits in that backlog, and {@code latest}
 * would skip it silently — leaving a membership {@code PENDING} for ever with a healthy producer, a healthy consumer, no
 * lag and no dead letter. That is the same argument item 19 made for the build order, and it survives the change of
 * channel because ignoring a frame cheaply is what makes replaying a large backlog harmless.</p>
 *
 * <p><b>What the first replay may legitimately produce is one dead letter, and it is not a defect.</b> The one
 * {@code PlanVerified} frame on the quality channel names a patient and a plan; if the membership it is about is no
 * longer {@code PENDING} it is ignored as already satisfied, and if that patient is unknown here it is refused
 * {@link Reason#UNKNOWN_PATIENT} and dead-lettered — which is the correct reading of an administrator's decision this
 * service cannot apply.</p>
 *
 * <p><b>Nothing end to end has been observed on this channel from this repository.</b> Every claim above about hc-admin's
 * behaviour is read off their source and off frames on the broker; no run of their service against this consumer has
 * happened. Do not add a producer to this repository to stand in for it — the only things that write to this channel are
 * hc-admin and, in tests, a raw Kafka producer inside the test.</p>
 */
@Component
public class PlanVerificationConsumer {

    /**
     * The one type on this channel that is addressed to this service.
     *
     * <p>A cross-repo contract held as a literal on both sides: hc-admin's {@code PlanVerifiedEvent.TYPE}. A rename here
     * is not a compile error there, so renaming it means changing both repositories and both sides' tests in the same
     * breath — the same rule {@code PatientEventType.PLAN_CHOSEN} carries for the other direction.</p>
     *
     * <p>⚠ <b>hc-admin holds this string twice and says not to merge the two.</b> Their {@code PlanVerificationEvent.TYPE}
     * is a fact about <em>this repository's</em> schema on the retired topic; their {@code PlanVerifiedEvent.TYPE} is a
     * fact about their channel. The values coincide because they describe one decision. Pinned here against the second
     * one.</p>
     */
    public static final String PLAN_VERIFIED = "PlanVerified";

    /**
     * The channel, for the one place this class names it: the tag on {@link #ignoredFrames}.
     *
     * <p>Not read by the binding, which is configured in {@code application.yml} — so this constant agreeing with the
     * YAML is a convention rather than a mechanism, and {@code PlanVerificationConsumerBindingIT} pins the YAML with its
     * own literal rather than with this field. A meter tag is operator-facing vocabulary and wants the topic's name.</p>
     */
    static final String CHANNEL = "admin.event";

    /** See {@link #ignoredFrames}. Named here so a test cannot assert a meter under a name nothing registers. */
    static final String IGNORED_METER_NAME = "events.consumption.ignored";

    /** See {@link #appliedFrames}. */
    static final String APPLIED_METER_NAME = "events.consumption.applied";

    /** See {@link #refusedFrames}. */
    static final String REFUSED_METER_NAME = "events.consumption.refused";

    /** See {@link #satisfiedFrames}. */
    static final String SATISFIED_METER_NAME = "events.consumption.satisfied";

    static final String IGNORED_METER_DESCRIPTION =
        "Frames read from another product's channel that this service is not addressed by. Not a fault: the ordinary case.";

    static final String APPLIED_METER_DESCRIPTION =
        "Plan verifications from hc-admin that moved a membership. The direct answer to 'are their decisions taking effect here'.";

    static final String REFUSED_METER_DESCRIPTION =
        "Plan verifications addressed to this service that it could not apply. Each one is also a dead letter.";

    static final String SATISFIED_METER_DESCRIPTION =
        "Plan verifications addressed to this service that asked for a state that already held — a replay, or a decision already applied.";

    static final String METER_BASE_UNIT = "events";

    private final Logger log = LoggerFactory.getLogger(PlanVerificationConsumer.class);

    private final MembershipService membershipService;

    private final ProfileRepository profileRepository;

    private final PlanVerificationRepository planVerifications;

    /**
     * How many frames on {@code admin.event} were none of this service's business.
     *
     * <h2>A counter because "nothing happened" and "nothing arrived" are the same log line</h2>
     *
     * <p>Item 32 is the whole reason this exists. That consumer was reviewed five times, deployed, healthy and serving —
     * and bound to nothing, because a compose file's empty {@code SPRING_CLOUD_FUNCTION_DEFINITION} stood the function
     * down. It was invisible precisely because a correctly bound consumer on a quiet topic looks identical to an unbound
     * one. <b>This channel is not quiet</b>, so the number of frames this consumer has declined is a direct answer to
     * "is it reading at all" — and the applied path's {@code INFO} answers the other half. Without it, the ignore path
     * this commit introduces would be the largest silent branch in the service.</p>
     *
     * <p>Shaped like {@link DroppedEventCounter}: one meter name with a {@code topic} tag rather than a name per
     * consumer, which is this repository's house shape and what a dashboard wants. Deliberately <b>not</b> tagged with
     * the frame's type — that string is hc-admin's to choose and a new one of theirs must not become a new time series
     * here.</p>
     *
     * <p><b>⚠ On its own this meter cannot tell "reading, none of it ours" from "reading, ours is no longer
     * recognised".</b> See {@link #appliedFrames}, which is the other half and the reason there are four of these
     * rather than one.</p>
     */
    private final Counter ignoredFrames;

    /**
     * How many of hc-admin's decisions this service actually put into effect.
     *
     * <h2>⚠ The alarm the inversion moved, and nothing else took its place</h2>
     *
     * <p><b>This exists because {@link #ignoredFrames} alone is not a health signal, and reading it as one would be a
     * mistake in the safe-looking direction.</b> {@link #PLAN_VERIFIED} is a literal pinned against a string hc-admin
     * owns. If it ever diverges — a rename, a change of casing, an envelope of theirs that stops carrying {@code type}
     * at all — then <b>every</b> verification takes the ignore path: the membership stays {@code PENDING}, nothing
     * enters the dead-letter queue, nothing logs above {@code TRACE}, and the ignored counter keeps climbing on their
     * entity churn exactly as it does when everything is well. Under item 19's rule that same drift dead-lettered
     * loudly and an operator found it; the ignore is still the right decision for a shared channel, but it moved the
     * alarm off the dead-letter queue and this is what takes its place.</p>
     *
     * <p>So the question worth alerting on is not "is anything arriving" but <b>"has this gone to zero while the
     * channel is busy"</b> — {@code rate(events_consumption_applied_events_total)} against a live
     * {@code events_consumption_ignored_events_total}. A drift shows as the second climbing while the first flatlines,
     * which is a shape no single meter has.</p>
     */
    private final Counter appliedFrames;

    /**
     * How many frames addressed to this service it could not apply — every one of which is also a dead letter.
     *
     * <p>The counterpart to reading {@code admin.event.hc-patient-dlq}: the queue holds the bytes, this makes "did we
     * refuse anything this week" answerable without reading it, which is the argument {@link DroppedEventCounter}
     * makes for its own meter. Incremented in {@link #refuse}, the one funnel every refusal passes through.</p>
     */
    private final Counter refusedFrames;

    /**
     * How many frames addressed to this service needed no work: a replay, or a decision that already holds.
     *
     * <p><b>Neither is a fault and both are ordinary</b> — hc-admin mints a fresh event id per press of their verify
     * button, so a recovery-republish is by their design indistinguishable from a second decision until this service
     * looks at the state. Counted rather than merely logged for the reason {@link #appliedFrames} gives: these are the
     * two paths that write nothing, announce nothing and throw nothing, so without a meter a test or an operator has
     * only an absence to look at — and an absence is equally consistent with a consumer that received nothing at all.
     * {@code PlanVerificationRoundTripIT} asserts on it for exactly that reason.</p>
     */
    private final Counter satisfiedFrames;

    public PlanVerificationConsumer(
        MembershipService membershipService,
        ProfileRepository profileRepository,
        PlanVerificationRepository planVerifications,
        MeterRegistry meterRegistry
    ) {
        this.membershipService = membershipService;
        this.profileRepository = profileRepository;
        this.planVerifications = planVerifications;
        this.ignoredFrames = register(meterRegistry, IGNORED_METER_NAME, IGNORED_METER_DESCRIPTION);
        this.appliedFrames = register(meterRegistry, APPLIED_METER_NAME, APPLIED_METER_DESCRIPTION);
        this.refusedFrames = register(meterRegistry, REFUSED_METER_NAME, REFUSED_METER_DESCRIPTION);
        this.satisfiedFrames = register(meterRegistry, SATISFIED_METER_NAME, SATISFIED_METER_DESCRIPTION);
    }

    /**
     * One shape for all four meters, so a fifth outcome cannot arrive under a different unit or a different tag.
     *
     * <p>Registered eagerly in the constructor rather than on first use, deliberately: a meter that appears only once
     * it has moved is a meter whose zero cannot be distinguished from its absence, and "applied has gone to zero" is
     * precisely the condition {@link #appliedFrames} exists to make visible.</p>
     *
     * <p><b>The four are mutually exclusive and jointly exhaustive</b>, which is what makes them add up to frames
     * received: every frame is ignored, applied, refused or satisfied, exactly once.
     * {@code PlanVerificationConsumerTest} asserts that accounting rather than leaving it to this sentence.</p>
     */
    private static Counter register(MeterRegistry registry, String name, String description) {
        return Counter
            .builder(name)
            .baseUnit(METER_BASE_UNIT)
            .description(description)
            .tag(DroppedEventCounter.TOPIC_DIMENSION, CHANNEL)
            .register(registry);
    }

    @Bean
    public Consumer<AdminEvent> adminEventConsumer() {
        return this::apply;
    }

    /**
     * Applies one verification, ignores a frame addressed to somebody else, or refuses one it cannot apply.
     *
     * <p><b>The type comes first, then the replay check, then the frame's own shape, then the patient, then the
     * membership.</b> Item 47 moved the type to the front and the ordering is load-bearing twice over. It is the only
     * check that can be answered without touching the database, and it is the one that applies to almost every frame:
     * 7433 of the 7435 on this channel on 2026-09-25 were none of this service's business, and paying a primary-key read
     * for each of them would be a database round trip per write in another product. It also has to be first to be
     * <em>correct</em> — an {@code EntityChanged} frame has a perfectly good {@code eventId}, so under item 19's ordering
     * it sailed past the blank-id guard and reached the type check, which then refused and dead-lettered it.</p>
     *
     * <p>The replay check leads everything after it because a redelivery is the ordinary case on an at-least-once channel
     * and must be cheap to dismiss. Everything after that is arranged so that a producer defect is reported as a producer
     * defect whether or not the patient it names happens to exist here.</p>
     */
    void apply(AdminEvent event) {
        if (event == null) {
            // The binder does not hand a function a null payload, so this is defensive rather than expected. Refused
            // rather than ignored, and it is the one frame on this channel that cannot be ignored on type: a null frame
            // has no type to read, so "not addressed to us" cannot be established. Under its own reason, because
            // NO_EVENT_ID would send the next reader to hc-admin's serialiser.
            throw refuse(Reason.NO_FRAME, "a null frame arrived on " + CHANNEL);
        }

        if (!PLAN_VERIFIED.equals(event.type())) {
            ignore(event);
            return;
        }

        String eventId = event.eventId();
        if (isBlank(eventId)) {
            throw refuse(
                Reason.NO_EVENT_ID,
                "a verification arrived with no eventId, so a replay of it could not be told from a first delivery"
            );
        }

        if (planVerifications.existsById(eventId)) {
            // At-least-once delivery working as specified. Not a fault, so not dead-lettered — and counted, because
            // this path writes nothing, announces nothing and throws nothing, so a meter is the only receipt.
            satisfiedFrames.increment();
            log.debug("Ignoring plan acknowledgement {} — already applied", eventId);
            return;
        }

        String email = subjectKey(event);
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
            // alreadySatisfied for why hc-admin's design makes this an ordinary event rather than an edge case, and
            // satisfiedFrames for why the one outcome with no output of any kind is the one that has to be counted.
            satisfiedFrames.increment();
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
        // After the write and after the ledger, so the meter counts decisions that took effect rather than frames that
        // reached this far. It is the one signal that goes to zero if PLAN_VERIFIED ever stops matching what hc-admin
        // sends — see appliedFrames, which is why that is worth alerting on and the ignored count is not.
        appliedFrames.increment();
        log.info(
            "Applied plan acknowledgement {}: membership {} for patient {} on plan {} is now ACTIVE",
            eventId,
            activated.getId(),
            patientId,
            planCode
        );
    }

    /**
     * The patient this verification is about.
     *
     * <h2>⚠ {@code data.subjectKey}, not {@code subject.email} — and this is the migration's one real trap</h2>
     *
     * <p>On the retired {@code patient-events-plan} the address was in {@code subject.email}, because the frame there was
     * this repository's own {@link PatientEvent} envelope. On {@code admin.event} the envelope is hc-admin's, and
     * {@code subject} means {@code (entityType, entityId)} — the {@code DirectoryLink} the administrator pressed the
     * button on — for every frame on the channel whatever its type. Their {@code PlanVerifiedEvent} therefore carries the
     * addressee in {@code data.subjectKey}, with the reason stated in as many words: <em>"it is on the frame because
     * hc-patient cannot ask — they have no route to this service"</em>.</p>
     *
     * <p><b>There is deliberately no fallback to {@code subject.email}</b>, and it is the one place this class refuses to
     * be tolerant. Reading a second location would be reading a field the channel's envelope says does not exist, so a
     * future frame that did carry an email in {@code subject} would mean something else and be silently accepted as an
     * addressee. Their own javadoc makes the mirror-image point about publishing it in both places: two places to read one
     * value is a way for them to disagree. A frame in the old envelope arriving here is a producer defect, and it refuses
     * loudly with this method's message naming the field it looked in.</p>
     *
     * <p>Lower-cased and trimmed here rather than trusted, although they do it at the publish point and pin it with a
     * test. The lookup is case-insensitive regardless; this is so that what is logged is what would have been joined on,
     * and because a wrong-cased key is the failure that is silent on both sides at once.</p>
     */
    private String subjectKey(AdminEvent event) {
        Object key = event.data() == null ? null : event.data().get("subjectKey");
        String email = key instanceof String named ? named : null;
        if (isBlank(email)) {
            throw refuse(
                Reason.NO_SUBJECT_KEY,
                "verification " +
                event.eventId() +
                " carries no data.subjectKey, which is where " +
                CHANNEL +
                " puts the addressee; its subject names " +
                (event.subject() == null ? "nothing" : event.subject().entityType() + "/" + event.subject().entityId()) +
                ", which is a record in hc-admin's database and not a patient here"
            );
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Records that a frame was not addressed to this service, and does nothing else.
     *
     * <p><b>This is the ordinary case on this channel and not an anomaly</b> — see the class javadoc. {@code TRACE}
     * rather than {@code DEBUG} because the volume is hc-admin's write rate and a line per frame at {@code DEBUG} would
     * make that level unusable on this service; {@link #ignoredFrames} is what makes the volume answerable without a log
     * at all, and is the reason ignoring cannot be confused with a consumer that is bound to nothing.</p>
     */
    private void ignore(AdminEvent event) {
        ignoredFrames.increment();
        log.trace("Ignoring a {} frame on {} — this service is addressed by {} only", event.type(), CHANNEL, PLAN_VERIFIED);
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
     * Refuses a verification naming a plan the membership does not hold.
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
     * <p><b>⚠ It was {@code catch (DuplicateKeyException)}, and the reason first given for broadening it was
     * WRONG. The wrong claim is kept here rather than deleted, because it is the third time on this item that a
     * paragraph asserted a measurement nobody could reproduce.</b> It read:</p>
     *
     * <blockquote>"Deleting the {@code existsById} guard to check the replay test could see it made this line
     * reachable, and what came out was <em>not</em> a {@code DuplicateKeyException}: the throw went straight past the
     * catch, the frame dead-lettered, and the membership stayed activated and announced."</blockquote>
     *
     * <p><b>An exception did escape, and it did not come from here.</b> Traced through the run that produced the
     * claim: all four occurrences are {@code IncorrectResultSizeDataAccessException} raised by
     * {@code profileRepository.findOneByEmailIgnoreCase} in {@link #apply}, on the query
     * {@code email =~ ^\Qkofi.replay@example.test\E$ returned non unique result} — a test-fixture artefact, since
     * {@code PlanVerificationRoundTripIT.givenAPendingMembership} saves a {@code Profile} on every call and that test
     * calls it twice for one address. One of the four is wrapped in a deliberate {@link PlanVerificationRefusedException}
     * from an unrelated refusal-path test. <b>None of them is this insert.</b></p>
     *
     * <p><b>And the narrow catch was never insufficient for what this insert actually throws.</b> Driven directly
     * through the repository proxy on the resolved classpath — {@code insert} with an {@code _id} that already
     * exists, against both a standalone and a replica-set {@code mongo:7.0.6} — the answer is
     * {@code DuplicateKeyException} both times, which {@code catch (DuplicateKeyException)} would have caught. There
     * is no {@code @Transactional} anywhere in {@code src/main/java}, so that probe is faithful to the listener path.</p>
     *
     * <p><b>The broadening still stands, on the argument below rather than on that history.</b> Nothing about the
     * corrected account weakens it: the reason to swallow everything here is what has already happened by the time
     * this line runs, not what any particular Mongo failure is called. The lesson is the misattribution itself —
     * an exception seen in a mutation run was assigned to the method being mutated rather than traced to its
     * origin, and it read as freshly measured for two commits.</p>
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
    private void record(AdminEvent event, String patientId, Membership activated, String planCode) {
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
     * a surprising one — a reader who sees the same event id four times should not go looking for four events.
     * {@link #refusedFrames} counts the same four, for the same reason: it measures <em>handlings</em> that refused,
     * not distinct frames, and a rate on it is read against that.</p>
     *
     * <p>⚠ <b>The counter lives here because this is the one funnel every refusal passes through, and that holds only
     * while every caller throws what this returns.</b> It is built rather than thrown by three call sites, inside
     * {@code orElseThrow} suppliers — which run only when the throw happens. A future call site that built a refusal
     * and did not throw it would count a dead letter that never occurred; there is no such call site, and adding one
     * would be a mistake on its own terms.</p>
     */
    private PlanVerificationRefusedException refuse(Reason reason, String message) {
        refusedFrames.increment();
        log.warn("Refusing a plan acknowledgement [{}]: {}", reason, message);
        return new PlanVerificationRefusedException(reason, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
