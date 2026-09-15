package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Membership}.
 *
 * <h2>Why this class exists at all, when twenty-two other entities write through their resource</h2>
 *
 * <p>Because a membership is the one document in this service that a <em>sibling product</em> is watching.
 * {@code MembershipResource} wrote through {@code membershipRepository.save} at three call sites and announced
 * {@link PatientEventType#PLAN_CHOSEN} at exactly one of them, so hc-admin learned that a plan had been chosen and
 * never learned what was decided about it. The announcement has to sit where <em>every</em> write passes, and the only
 * thing all three passed through was the Mongo repository — which cannot carry a rule, being a Spring Data interface
 * this repo does not implement and a layer {@code TechnicalStructureTest} forbids reaching back into {@code service}.
 * So the seam is created rather than found. Backlog item 27.</p>
 *
 * <p><strong>A private helper on the resource would have worked today and been wrong within the week.</strong> Item
 * 19's inbound {@code patient-events-plan} consumer writes {@code PENDING → ACTIVE} and lives in this package since
 * 2026-09-10 ({@link net.jojoaddison.service.event.PlanVerificationConsumer}), where it could not have called a
 * private method on a {@code web} class at all. It inherited the announcement without a new call site, which is what
 * item 27 was for — and it needed one thing the seam did not have, {@link #activateIfPending}, for the reason that
 * method's own javadoc gives. This repo has twice in one week proved that a
 * rule written at one call site is wrong one file away — item 19's own write guard, where {@code PUT} had drifted from
 * {@code PATCH}, and item 18's unkeyed-event guard, which {@code CareDelegationService} broke within the hour and
 * which had to move into {@link PatientEventPublisher}. Add a write path here, not beside here.</p>
 *
 * <h2>What is announced, and the rule that decides</h2>
 *
 * <p><strong>A creation always announces; an update announces when the status this service persisted differs from the
 * one the stored document held.</strong> Deliberately not "announce on {@code PUT} and {@code PATCH}" — that reading
 * would republish every time a patient renamed their own membership, and it would not cover item 19's inbound consumer
 * at all. Written on the persisted status, the rule and item 19's write guard agree without either knowing about the
 * other: a non-administrator's requested status is discarded and the stored one carried over, so their update compares
 * equal and says nothing.</p>
 *
 * <p><strong>The comparison is against the stored value, never against the request body.</strong> Both {@code PUT} and
 * {@code PATCH} already read the stored document for their ownership check, so the caller hands the held status in and
 * it costs no extra query. Comparing against the body would announce on every request from a caller who changed
 * nothing, which is most of them.</p>
 *
 * <p><strong>Two producers may now write one row on hc-admin's side, and that is safe rather than overlooked.</strong>
 * Once item 19's consumer sets {@code ACTIVE} and announces, and an administrator here sets {@code ACTIVE} and
 * announces, the same terminal state can arrive twice. Their write is an idempotent upsert on the subject key and the
 * plan group is replaced wholesale, so the second frame changes nothing — but it is stated here because a second frame
 * reads as a bug to whoever finds it.</p>
 *
 * <p><strong>More frames are self-healing only for transitions, and that limit is worth knowing before relying on
 * it.</strong> hc-admin dispatches {@code PlanChosen} as {@code UPDATE_ONLY} and writes nothing at all for a subject
 * it holds no link for — so a frame arriving before their {@code AccountCreated} landed is lost, and because a
 * repeat of the same status is suppressed here by design, no later frame recreates it. Every <em>transition</em> after
 * that heals the row; a membership whose only frame was dropped stays missing until its status next moves.</p>
 *
 * <p><strong>And a supersession announces nothing at all, which is the one write here that is deliberately
 * silent.</strong> Since 2026-09-15 a second plan choice cancels the patient's earlier pending one, so that the
 * verifier's "exactly one {@code PENDING}" rule holds by construction — backlog item 40. That cancellation is not
 * announced, because hc-admin keys one plan group per patient on {@code membershipId} and replaces it wholesale, so a
 * frame about the superseded membership would arrive <em>after</em> the creation frame on the same partition and
 * leave their row naming a cancelled membership instead of the choice the patient just made. The whole argument is on
 * {@link #supersedeOtherPendingChoices}, where the write is.</p>
 *
 * <h2>Three things this deliberately does not do</h2>
 *
 * <p><strong>{@code DELETE} announces nothing.</strong> There is no event type for a deleted membership and no
 * disposition on hc-admin's side that clears the plan group, so a deletion would leave {@code plan_membership_id} and
 * its status on their directory row for ever — a phantom nothing retires. Nothing in either client deletes a
 * membership ({@code DELETE} is {@code ROLE_ADMIN}-only CRUD), so it is recorded rather than built. Revisit it if a
 * deletion path is ever added to a client. Backlog item 27.</p>
 *
 * <p><strong>A plan change with no status change announces nothing either, so {@code plan_code} can go stale
 * permanently.</strong> A patient moving {@code PEAR → MELON} on an {@code ACTIVE} membership leaves hc-admin showing
 * the tier they left. That follows from item 18's decision — the rule is written on the status and only the status —
 * and it is not a bug against that decision, but the event is called {@code PlanChosen} and carries the plan, so the
 * gap is worth naming rather than leaving for someone to find on a dashboard. Widening the rule to "status or plan"
 * is a decision for item 18, not for this class.</p>
 *
 * <p><strong>This class owns the three save paths the REST API exposes, and that is a convention rather than a
 * boundary anything enforces.</strong> {@code MembershipResource} still injects {@code MembershipRepository} and calls
 * {@code deleteById} on it directly, and {@code DevelopmentDataInitializer} saves seeded memberships straight through
 * the repository — so a fourth {@code membershipRepository.save(...)} elsewhere would compile, pass
 * {@code TechnicalStructureTest} and announce nothing. The seam removes the <em>reason</em> to write one; it cannot
 * stop you. If that ever needs teeth, the enforcement would be an ArchUnit rule naming this class as the only caller
 * of {@code MembershipRepository.save}.</p>
 */
@Service
public class MembershipService {

    private final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final MembershipRepository membershipRepository;

    private final ProfileRepository profileRepository;

    private final PatientEventPublisher events;

    private final MongoTemplate mongoTemplate;

    public MembershipService(
        MembershipRepository membershipRepository,
        ProfileRepository profileRepository,
        PatientEventPublisher events,
        MongoTemplate mongoTemplate
    ) {
        this.membershipRepository = membershipRepository;
        this.profileRepository = profileRepository;
        this.events = events;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Save a new membership, and say so.
     *
     * <p>Saved first, then announced. The event is a notification, never the mechanism — see
     * {@link #announceChosenPlan}.</p>
     *
     * <p><strong>Unconditionally, unlike the two updates.</strong> There is no held status to compare against, so
     * there is nothing the rule could ask. This frame <em>fills the plan group on a directory row
     * {@code AccountCreated} already made</em> — it does not create one: hc-admin treats {@code PlanChosen} as
     * {@code UPDATE_ONLY} and writes nothing for a subject it does not know, on the reasoning that a plan choice for
     * an unknown patient means their earlier events were missed rather than that a new person exists. A membership an
     * administrator creates already {@code ACTIVE} is announced as {@code ACTIVE}, because this reports what was
     * written rather than what was asked for.</p>
     *
     * <p><strong>Unconditional is only safe while every creation has a status to announce, and that is a guarantee
     * somebody else keeps.</strong> {@code MembershipResource.statusOnCreate} defaults an unnamed status to
     * {@code PENDING} for every caller, so the frame below always carries the key. It did not until 2026-09-09 —
     * an administrator naming no status published {@code membershipId}, {@code planCode} and {@code planName} with no
     * {@code status}, and hc-admin's {@code setOrUnset} dropped {@code plan_status} from the row, so the membership
     * never appeared on the queue that exists to get it decided. If a fourth write path ever creates a membership,
     * this is the invariant it has to keep.</p>
     *
     * @param membership the entity to save, already stripped of anything the caller may not decide.
     * @return the persisted entity.
     */
    public Membership save(Membership membership) {
        log.debug("Request to save Membership : {}", membership);
        Membership result = membershipRepository.save(membership);
        // Before the announcement, so the frame describes a state in which the invariant holds. See
        // supersedeOtherPendingChoices for why the supersession itself says nothing.
        supersedeOtherPendingChoices(result);
        announceChosenPlan(result);
        return result;
    }

    /**
     * Update a membership, announcing it if the update decided where it stands.
     *
     * @param membership the entity to save.
     * @param statusHeld the status the stored document held before this write, read by the caller for its ownership
     *     check. Never the status in the request body — see the class javadoc.
     * @return the persisted entity.
     */
    public Membership update(Membership membership, MembershipStatus statusHeld) {
        log.debug("Request to update Membership : {}", membership);
        Membership result = membershipRepository.save(membership);
        // On every write that leaves a membership PENDING, not only on a creation — an administrator may send an
        // ACTIVE membership back to PENDING, which is the second way into two pending choices. See
        // supersedeOtherPendingChoices.
        supersedeOtherPendingChoices(result);
        announceIfDecided(result, statusHeld);
        return result;
    }

    /**
     * Partially update a membership, merging only the fields the request carried, and announcing it if the merge
     * decided where the membership stands.
     *
     * @param membership the entity to update partially.
     * @param statusHeld the status the stored document held before this write. See {@link #update}.
     * @return the persisted entity, or empty when there is no such membership.
     */
    public Optional<Membership> partialUpdate(Membership membership, MembershipStatus statusHeld) {
        log.debug("Request to partially update Membership : {}", membership);

        Optional<Membership> result = membershipRepository
            .findById(membership.getId())
            .map(existingMembership -> {
                if (membership.getPatientId() != null) {
                    existingMembership.setPatientId(membership.getPatientId());
                }
                if (membership.getName() != null) {
                    existingMembership.setName(membership.getName());
                }
                if (membership.getDescription() != null) {
                    existingMembership.setDescription(membership.getDescription());
                }
                if (membership.getStatus() != null) {
                    existingMembership.setStatus(membership.getStatus());
                }
                if (membership.getMemberNumber() != null) {
                    existingMembership.setMemberNumber(membership.getMemberNumber());
                }
                if (membership.getPlan() != null) {
                    existingMembership.setPlan(membership.getPlan());
                }
                if (membership.getStartDate() != null) {
                    existingMembership.setStartDate(membership.getStartDate());
                }
                if (membership.getRenewalDate() != null) {
                    existingMembership.setRenewalDate(membership.getRenewalDate());
                }
                if (membership.getCreatedDate() != null) {
                    existingMembership.setCreatedDate(membership.getCreatedDate());
                }
                if (membership.getModifiedDate() != null) {
                    existingMembership.setModifiedDate(membership.getModifiedDate());
                }
                if (membership.getCreatedBy() != null) {
                    existingMembership.setCreatedBy(membership.getCreatedBy());
                }
                if (membership.getModifiedBy() != null) {
                    existingMembership.setModifiedBy(membership.getModifiedBy());
                }

                return existingMembership;
            })
            .map(membershipRepository::save);

        // Nothing merged means nothing decided: an absent membership has no persisted status to have moved, and
        // announcing one would put a membership id on the topic that hc-admin creates a plan group for and never
        // clears. The same reasoning keeps the supersession behind the same guard — there is no pending choice to
        // supersede for a membership that was not written.
        result.ifPresent(saved -> {
            supersedeOtherPendingChoices(saved);
            announceIfDecided(saved, statusHeld);
        });
        return result;
    }

    /**
     * One patient's memberships awaiting a decision.
     *
     * <p>For the inbound {@code patient-events-plan} consumer, which is handed an email rather than a membership id
     * and has to decide which membership an acknowledgement applies to. It returns <em>all</em> of them because item
     * 19's rule is "the single {@code PENDING} one, refusing rather than guessing if there is not exactly one" — the
     * count is the decision, so the caller has to be able to see it.</p>
     *
     * @param patientId the patient, never null.
     * @return their {@code PENDING} memberships, in no particular order.
     */
    public List<Membership> pendingFor(String patientId) {
        return membershipRepository.findByPatientIdAndStatus(patientId, MembershipStatus.PENDING);
    }

    /**
     * One patient's memberships already in force.
     *
     * <p>For the inbound consumer's second question, which only arises because of how hc-admin publishes: they
     * republish a verification unconditionally with a <b>fresh event id</b> every time an administrator presses the
     * button, deliberately, because republishing is how they recover a lost frame. So a repeat is not a redelivery
     * and the event-id ledger does not suppress it — it arrives asking for a state that already holds. Without this
     * the consumer would refuse it, and every successful recovery-republish would dead-letter.</p>
     *
     * @param patientId the patient, never null.
     * @return their {@code ACTIVE} memberships, in no particular order.
     */
    public List<Membership> activeFor(String patientId) {
        return membershipRepository.findByPatientIdAndStatus(patientId, MembershipStatus.ACTIVE);
    }

    /**
     * Moves one membership from {@code PENDING} to {@code ACTIVE} if it is still {@code PENDING}, and announces it.
     *
     * <h2>Why this is a conditional update and not a third caller of {@link #update}</h2>
     *
     * <p><strong>Because item 19's consumer is the second writer item 30 said would make the lost-update window
     * routine, and it would have had to open that window itself to use the seam as it stood.</strong> {@link #update}
     * takes a {@code statusHeld} the caller has already read, which for the web layer costs nothing — {@code PUT} and
     * {@code PATCH} read the stored document anyway for their ownership check. A consumer has no such read, so it
     * would have to do one, and read-then-save is not the same operation as compare-and-set: between the two an
     * administrator's {@code PATCH} setting {@code CANCELLED} would be silently overwritten with {@code ACTIVE}, and
     * silently is exact — {@code announceIfDecided} would receive the stale {@code PENDING}, compare unequal, and
     * publish the overwrite to hc-admin as though it were the decision. That is item 30's defect with a sibling
     * product holding the pen, and item 30's own note says the fix is <em>"one read or a version, not a third
     * guard."</em></p>
     *
     * <p><strong>So the held status stops being something a caller reads and becomes part of the write.</strong> The
     * criterion <em>is</em> {@code status == PENDING}, MongoDB applies {@code findAndModify} atomically to the
     * document, and an empty answer means somebody else moved it first — which the caller must treat as a refusal
     * rather than retry, because whatever they decided is a real decision and this is not. The announcement can then
     * pass {@code PENDING} as the held status as a <em>fact</em> rather than as a hopeful earlier read.</p>
     *
     * <p><strong>What this does not close, stated rather than left to be found.</strong> It makes one document's
     * transition atomic; it does not make the consumer's whole rule atomic. The caller counts the patient's
     * {@code PENDING} memberships before calling this, and a second one created in the window between that count and
     * this write would not be seen — so the <em>"exactly one pending"</em> rule is checked against a snapshot, and the
     * residue is <b>any</b> second {@code PENDING} membership created in that window, on any tier. It cannot be
     * closed here: production runs MongoDB standalone with no replica set, which is why
     * {@link PatientEventPublisher} has no outbox either, so there is no transaction to put the count and the write
     * inside.</p>
     *
     * <p><strong>What it does guarantee is narrower and worth stating exactly, because the tempting overstatement is
     * wrong.</strong> It is <em>not</em> that the plan check bounds the race — this is called with the id read from
     * the snapshot, so no concurrently created membership can be activated whatever its plan, and citing the plan
     * check as a bound is inert. What holds is that <b>the wrong membership cannot be activated</b>: this writes only
     * the id it was given, and if that document has moved out of {@code PENDING} the criterion matches nothing and
     * the caller refuses. So the failure available in the window is a verification refused or applied to the older of
     * two pending choices — never a membership activated that nobody verified.</p>
     *
     * @param membershipId the membership to activate.
     * @return the membership as persisted, or empty when it was not {@code PENDING} by the time the write landed —
     *     including when there is no such membership at all.
     */
    public Optional<Membership> activateIfPending(String membershipId) {
        log.debug("Request to activate Membership if pending : {}", membershipId);
        Membership persisted = mongoTemplate.findAndModify(
            Query.query(Criteria.where("_id").is(membershipId).and("status").is(MembershipStatus.PENDING)),
            new Update().set("status", MembershipStatus.ACTIVE),
            // The document AFTER the write. announceChosenPlan reads the status off what it is given and reports what
            // was written rather than what was asked for, so handing it the pre-image would publish PENDING.
            FindAndModifyOptions.options().returnNew(true),
            Membership.class
        );
        if (persisted == null) {
            return Optional.empty();
        }
        // PENDING as a fact rather than as a read: nothing else could have satisfied the criterion above.
        announceIfDecided(persisted, MembershipStatus.PENDING);
        return Optional.of(persisted);
    }

    /**
     * Cancels every <em>other</em> {@code PENDING} membership this patient holds, so that the one just written is
     * their only one.
     *
     * <h2>The invariant, and why it is established here rather than refused at the door</h2>
     *
     * <p><strong>At most one {@code PENDING} membership per patient, always.</strong> Item 19's inbound consumer is
     * handed an email rather than a membership id and applies a verification to the patient's <em>single</em> pending
     * choice, refusing rather than guessing if there is not exactly one — and that refusal was right and stayed right,
     * while nothing on the writing side agreed with it. {@code POST /api/memberships} guarded only against a
     * client-supplied id, so every tap of CHOOSE wrote another pending membership. A patient who tapped three times
     * put their own record into a state the verifier would then refuse to act on for ever, and was told nothing: the
     * app said "Awaiting confirmation" while an administrator pressed verify and the frame dead-lettered. That is a
     * live incident, 2026-09-11, and backlog item 40.</p>
     *
     * <p><strong>Replacing rather than refusing with a 409, decided by the architect.</strong> A patient who picked
     * the wrong tier would otherwise be stuck until an administrator acted, which is a worse failure than the one
     * being fixed — somebody changing their mind from PAWPAW to MELON before the back office has looked expects it to
     * work. So the second choice succeeds with 201 and the first is superseded, and the consumer's "exactly one"
     * rule holds by construction instead of by hope.</p>
     *
     * <p><strong>{@code CANCELLED}, and the imprecision is accepted rather than unnoticed.</strong> "Cancelled" reads
     * as the patient's own act of ending a subscription, and this is a consequence of their choosing again — a
     * {@code SUPERSEDED} constant would say what happened. It is not available: {@link MembershipStatus} shipped five
     * values <em>because</em> the i18n bundles in {@code web} and {@code mobile} already promised exactly those five
     * in every language each ships, and hc-admin renders only {@code ACTIVE}, {@code CANCELLED}, {@code EXPIRED} and
     * {@code PENDING}. A sixth value is a cross-product contract change, and it would render as a raw constant or as
     * nothing on three screens in three repositories. {@code CANCELLED} already exists everywhere and already means
     * "this membership is over", which is true of the record either way.</p>
     *
     * <p><strong>Cancelled, never deleted.</strong> Nothing patient-owned is deleted here — sixteen resources refuse
     * {@code DELETE} to anyone but an administrator — and the superseded record is the evidence of what the patient
     * asked for and when. The production recovery deleted two spares by hand and very nearly deleted the one being
     * kept; this leaves them readable instead.</p>
     *
     * <h2>Why this announces nothing</h2>
     *
     * <p><strong>Because hc-admin holds one plan group per patient, keyed on {@code membershipId} and replaced
     * wholesale, and a supersession frame would overwrite the choice this write just made.</strong> Both frames carry
     * the same subject key, so they land on the same partition in order: the creation announces the new membership as
     * {@code PENDING}, and a second frame announcing the old one as {@code CANCELLED} would arrive after it and leave
     * their directory row naming a cancelled membership. <b>The patient's new choice would never appear on the queue
     * their console filters on {@code planStatus=PENDING}</b> — dequeued with no decision recorded anywhere, which is
     * the same failure {@link #announceIfDecided} refuses to cause by announcing a cleared status, arriving from the
     * other end of the lifecycle.</p>
     *
     * <p><strong>Nothing is lost by the silence, and that is checkable rather than hopeful.</strong> hc-admin never
     * held the superseded membership as a separate row — their row holds the patient's current plan group, which is
     * exactly what the creation frame carries. Silence leaves them describing the choice that is in force; announcing
     * would leave them describing one that is not.</p>
     *
     * <p><strong>The secondary reason, which is the one a reader will think of first.</strong> A {@code CANCELLED}
     * frame reads as the patient having quit. Nobody quit — they chose again — and a console showing a cancellation
     * that never happened is the sort of thing somebody acts on.</p>
     *
     * <h2>Atomicity, and what it does not close</h2>
     *
     * <p><strong>Written then swept, never counted then written.</strong> Each supersession is a
     * {@code findAndModify} whose criterion <em>is</em> {@code status == PENDING}, so a document moves only if it is
     * still pending when that write lands — the same compare-and-set shape as {@link #activateIfPending}, for the same
     * reason: a read-then-save would silently overwrite a decision an administrator took in the gap. There is no
     * transaction to put the writes in; production runs MongoDB standalone with no replica set.</p>
     *
     * <p><strong>The enumerating query is not the guard, and it deliberately does not repeat it.</strong> It selects
     * the patient's <em>other</em> memberships, all of them, and the compare-and-set decides which ones move. Adding
     * {@code status == PENDING} to the read as well would be the same rule written twice, and the copy that is not
     * the write is the one that can drift — worse, it would <b>shadow</b> the real guard: with both in place,
     * deleting the criterion from the {@code findAndModify} left all 26 integration tests green, because no test that
     * can be written without a second thread can tell the two apart. One rule, in the place that enforces it, where a
     * mutation can be seen.</p>
     *
     * <p><strong>And it is a loop rather than one {@code updateMulti}, which was the first form.</strong> The
     * superseded documents end up <b>in hand rather than merely counted</b>, so the decision not to announce them is
     * one this method takes with the data available rather than one it falls into for want of it — and a test can
     * therefore observe the silence. The log line names the ids for the same reason the consumer's refusal does.</p>
     *
     * <p><strong>Two racing creations cannot both leave a {@code PENDING} membership, and the argument is short
     * enough to check.</strong> Each request inserts before it sweeps, so two surviving pending memberships would
     * need each sweep to have run before the other's insert — {@code insertA &lt; sweepA &lt; insertB &lt; sweepB
     * &lt; insertA}, which is a cycle. What <em>is</em> available in that window is the opposite outcome: each sweep
     * sees the other's insert and they cancel each other, leaving the patient <b>zero</b> pending memberships. That is
     * a refusal the verifier reports loudly as {@code NO_PENDING_MEMBERSHIP}, and one more tap of CHOOSE repairs it —
     * where the defect this replaces was silent and un-repairable by the patient. Stated rather than left to be
     * found, exactly as {@link #activateIfPending} states its own residue.</p>
     *
     * <p><strong>It repairs as well as prevents.</strong> The sweep cancels <em>every</em> other pending membership,
     * not just one, so a patient who already holds three of them is reduced to one by their next choice without
     * anybody running anything. The one-off cleanup in
     * {@link net.jojoaddison.config.dbmigrations.SinglePendingMembershipMigration} exists because the patients in the
     * incident should not have to tap CHOOSE again to get their records into a state the back office can act on.</p>
     *
     * <h2>Two things it deliberately does not touch</h2>
     *
     * <p><strong>A membership that is not itself {@code PENDING} supersedes nothing.</strong> The invariant is about
     * pending choices, so an administrator creating an {@code ACTIVE} membership directly leaves a pending one alone:
     * whether an approval elsewhere moots a request the patient made is a back-office judgement, and this method is
     * not the place to take it. The verifier still sees exactly one pending membership either way.</p>
     *
     * <p><strong>A membership with no owner supersedes nothing either, and that guard is load-bearing.</strong>
     * {@code PatientScope.requirePatientIdForWrite} lets an unrestricted caller create a record with no
     * {@code patientId} — a deliberate allowance for the reference-shaped entities — so without this check a sweep
     * keyed on a null owner would match <em>every</em> ownerless pending membership in the collection and cancel the
     * lot as though they belonged to one person. Blank counts as absent, the same reading
     * {@link PatientEventPublisher} applies to a subject key.</p>
     *
     * <p>{@link #activateIfPending} is not routed through here because it can only ever produce {@code ACTIVE} — it
     * never leaves a membership pending, so there is nothing for it to supersede.</p>
     *
     * @param chosen the membership as persisted, never null.
     */
    private void supersedeOtherPendingChoices(Membership chosen) {
        if (chosen.getStatus() != MembershipStatus.PENDING) {
            return;
        }
        String patientId = chosen.getPatientId();
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        // DELIBERATELY NOT narrowed to PENDING. Filtering here as well would express the rule twice, and the copy that
        // is not the compare-and-set is the one that can drift — it also SHADOWS the real guard, so deleting
        // `status == PENDING` from the write below becomes invisible to every test that can be written without a
        // second thread. Measured: with both filters in place, removing the criterion from the findAndModify left all
        // 26 integration tests green. The patient's whole membership list is a handful of documents and this runs only
        // when somebody chooses a plan.
        //
        // Written against Membership.class rather than the collection name so that the mapper translates the property
        // names to their stored fields and, crucially, converts the id: Membership._id is an ObjectId and chosen.getId()
        // is its hex spelling, so a criterion built against the raw collection would match nothing and report success.
        Query theirOtherMemberships = Query.query(Criteria.where("patientId").is(patientId).and("_id").ne(chosen.getId()));
        List<String> superseded = new ArrayList<>();
        for (Membership other : mongoTemplate.find(theirOtherMemberships, Membership.class)) {
            Membership cancelled = mongoTemplate.findAndModify(
                // The compare-and-set. The query above is only how the documents are found; THIS is what makes each
                // move conditional, so a decision taken between the two is kept rather than overwritten — the same
                // reasoning and the same shape as activateIfPending. A null answer means somebody else moved it
                // first, which needs nothing done about it: it is no longer pending, which is all this wanted.
                Query.query(Criteria.where("_id").is(other.getId()).and("status").is(MembershipStatus.PENDING)),
                new Update().set("status", MembershipStatus.CANCELLED),
                FindAndModifyOptions.options().returnNew(true),
                Membership.class
            );
            if (cancelled != null) {
                // NOTHING IS ANNOUNCED HERE, and the superseded document is in hand rather than merely counted so
                // that the silence is a decision this method takes rather than a consequence of not having the data
                // to announce. The argument is in this method's javadoc; do not add a publish to this loop without
                // reading it — it would take the patient's new choice off hc-admin's queue.
                superseded.add(cancelled.getId());
            }
        }
        if (!superseded.isEmpty()) {
            // The ids, not a count, and at INFO. The consumer's refusal named all three offending ids and that is
            // what made the incident diagnosable in minutes rather than hours; this is the other half of the same
            // conversation, written at the end that causes it.
            log.info("Membership {} is patient {}'s pending choice; superseded {} to CANCELLED", chosen.getId(), patientId, superseded);
        }
    }

    /**
     * Announces the membership when this write moved its status, and stays silent when it did not.
     *
     * <p>The single place the rule lives, for every write path that has a "before" — which today is {@code PUT} and
     * {@code PATCH} and tomorrow is item 19's inbound consumer. It is written here rather than at each caller for the
     * reason the class javadoc gives, and rather than on the verb because the verb is not what changed: a patient
     * renaming their membership sends the same {@code PATCH} an administrator approving it does.</p>
     *
     * <p>{@link Objects#equals} rather than {@code !=}: a membership written before a status existed holds null, and
     * null → {@code PENDING} is a decision, not the absence of one.</p>
     *
     * <p><strong>The comparison is not symmetric, and making it symmetric was a defect caught in review.</strong> A
     * status arriving where there was none is a decision; a status being <em>cleared</em> is not. Announced, a cleared
     * status carries no {@code status} key at all, hc-admin's {@code setOrUnset} removes {@code plan_status}, and the
     * row silently stops matching the {@code planStatus=PENDING} filter their queue is built on — <em>dequeued with no
     * decision recorded anywhere</em>. Before this class announced on updates the same mistake cost only the local
     * document: hc-admin went on showing a stale {@code PENDING}, which is wrong but visible and still actionable.
     * Staying silent restores that, which is the better failure of the two.</p>
     *
     * <p><strong>No update path still produces the case this was written for, and this guard stays anyway.</strong>
     * It was written because {@code MembershipResource.statusForUpdate} handed an administrator back exactly what the
     * body carried, so saving the generated update form with the status select on its blank
     * {@code <option [ngValue]="null">} persisted a null; backlog item 30 closed that on 2026-09-09 by carrying the
     * stored status over when a request does not name one. Two ways in remain, which is why this is not dead code: a
     * document whose stored status is <em>already</em> null carries null over and still arrives here, and this is a
     * {@code service} method that any caller in this package may reach — item 19's inbound consumer among them —
     * without passing the resource's guards at all. This class is the seam precisely so that a rule does not depend on
     * the one caller that exists today.</p>
     *
     * <p><strong>Say "no update path" rather than "the web layer", because the difference was a live defect.</strong>
     * {@link #save} does not come through here at all, and {@code statusOnCreate} had the identical hole from the
     * identical {@code <select>} — so while an update with a cleared status was contained by the guard below, a
     * <em>creation</em> with one announced unconditionally and put a frame with no {@code status} key on the topic,
     * which is the dequeue-with-no-decision failure described above happening at the other end of the lifecycle. It
     * was found by review of item 30 and closed with it. The wording matters because a sentence that says "the web
     * layer" invites the reader to check one verb and generalise, which is how this repo has now produced the same
     * class of defect four times.</p>
     */
    private void announceIfDecided(Membership persisted, MembershipStatus statusHeld) {
        if (persisted.getStatus() == null) {
            // Nothing was decided — see the javadoc. Silence here is what keeps a cleared status a local defect
            // instead of a cross-product one.
            return;
        }
        if (Objects.equals(persisted.getStatus(), statusHeld)) {
            return;
        }
        announceChosenPlan(persisted);
    }

    /**
     * Says on {@code patient-events} that this patient chose a plan, so that hc-admin can raise the pending
     * membership for action.
     *
     * <p><strong>Keyed on the patient's email, resolved from their profile rather than taken from the token.</strong>
     * Usually they are the same person, but an administrator creating a membership for somebody is not, and keying on
     * the caller would file that event under the administrator — on a different partition from the patient's own
     * account and onboarding events, which is exactly the ordering guarantee
     * {@link net.jojoaddison.service.event.PatientEventPublisher} exists to keep. Same resolution
     * {@code CareDelegationService} does for the same reason.</p>
     *
     * <p><strong>What travels.</strong> The membership id, the plan and the status actually persisted — not the
     * literal {@code PENDING}, because an administrator may legitimately have created or approved an {@code ACTIVE}
     * one, and an event should report what was written. That the status is read off the saved document rather than
     * hardcoded is what makes this method usable for a decision as well as a choice: hc-admin keys the plan group on
     * {@code membershipId} and replaces it wholesale, so a second frame carrying {@code ACTIVE} retires the row from
     * the panel their console filters on {@code planStatus=PENDING}. The field names are the honest ones for this document: backlog item 18
     * asks for the plan "code" and "name", and there is no {@code code} field on {@code Membership} — both clients'
     * {@code choosePlan} write the plan's code into {@code plan} and its display name into {@code name}, so those are
     * published as {@code planCode} and {@code planName}. See {@link PatientEventType#PLAN_CHOSEN} for the rest of the
     * contract, including why the missing {@code memberNumber} and {@code renewalDate} are not an oversight.</p>
     *
     * <p><strong>An event we cannot key is refused by {@link PatientEventPublisher}, not by this method.</strong> That
     * guard lived at this call site for one commit and was immediately shown to be in the wrong place —
     * {@code CareDelegationService} has the same shape and had the same hole. It is a rule about the envelope, so it
     * belongs to the envelope; read it there. This method's only part in it is to pass a null email rather than invent
     * one.</p>
     *
     * <p><strong>What refusing does and does not buy.</strong> It is <em>not</em> a louder failure: nothing in this
     * stack alerts on a log line — {@code deploy/observability/alert-rules.yml} says so in as many words, the JVMs push
     * OTLP and no log pipeline exists — so an unannounced membership is unnoticed either way, and nothing here
     * enumerates {@code PENDING} memberships to notice it later. What it buys is narrower and still worth having: no
     * unattributable record on a retained, replayed topic; the diagnosis in the repository whose operator caused it
     * rather than in hc-admin's log; and a behaviour that is correct now their item 48 has landed, where publishing an
     * unkeyed frame would fail again. If this condition should be alertable, that needs a counter and the Micrometer
     * bridge the alert-rules file already names as outstanding — not a WARN.</p>
     *
     * <p><strong>Nothing an operator or a caller does may fail the request</strong> — the membership is already saved
     * by the time this runs, and a charged-but-told-it-failed response is worse than a lost event. Only the profile
     * lookup is guarded, because it is a Mongo query this method introduced. <em>One thing may still throw, and is
     * meant to:</em> {@link PatientEventPublisher#assertNothingClinical} rejects a payload carrying a clinical key, and
     * that is a bug in this service rather than anything a caller did. A blanket catch would convert that deliberate
     * shout into a WARN and lose every event in production while this method reported success. It surfaces as a 500,
     * and {@code MembershipPlanEventIT} exercises the real publisher, so it cannot reach production without failing
     * CI.</p>
     */
    private void announceChosenPlan(Membership membership) {
        Map<String, Object> data = new HashMap<>();
        // Unconditional, and the only one: it is read off the saved document after the save, and it is the field
        // hc-admin keys the whole plan group on — a frame without it describes nothing and they leave it alone.
        data.put("membershipId", membership.getId());
        putIfPresent(data, "planCode", membership.getPlan());
        putIfPresent(data, "planName", membership.getName());
        // The name, not the enum: the wire shape should not move if the enum's serialization ever does.
        putIfPresent(data, "status", membership.getStatus() == null ? null : membership.getStatus().name());
        events.publish(PatientEventType.PLAN_CHOSEN, patientEmail(membership.getPatientId()), null, membership.getPatientId(), data);
    }

    /**
     * Puts a payload field, or leaves it out when there is nothing to say.
     *
     * <p><strong>An absent field is absent, not null.</strong> {@code Membership.plan} carries no {@code @NotNull}, so
     * an administrator creating a membership through the CRUD path with no tier named used to publish
     * {@code planCode: null} and {@code planName: null} beside a real membership id — hc-admin's
     * {@code DirectoryProjectionService} names that line of ours in a comment and handles it, having been caught by it
     * once. Sending a null is asking a consumer to distinguish "we have no plan" from "we forgot the plan", and
     * nothing on the wire lets them.</p>
     *
     * <p>Nothing changes downstream and that was checked rather than assumed: their {@code SiblingEventParser} reads
     * each key with a helper that answers null for an absent node, and their {@code setOrUnset} removes the field for
     * a null <em>or blank</em> value. Blank is treated as absent here for the same reason
     * {@link PatientEventPublisher} treats a blank subject key as no key — every consumer in the estate already reads
     * the two identically, so a producer that distinguishes them is the only thing that does.</p>
     */
    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    /**
     * The email of the patient a membership belongs to, or null when there is no profile to read it from.
     *
     * <p>Null rather than the caller's own address: an event filed under the wrong person is worse than one filed
     * under nobody. Note that "under nobody" means <em>not filed at all</em> — {@link PatientEventPublisher} refuses a
     * frame it cannot key rather than sending one every consumer discards.</p>
     *
     * <p>Falls back to a lookup by id because {@code patientId} was added after some profiles were written and those
     * carry only their own id — the same fallback {@code CareDelegationService} applies, and the one
     * {@code PatientScope} applies when resolving the other direction. The qualifier matters: {@code PatientScope}
     * goes profile → id and this goes id → profile, so they are duals rather than the same call, and a reader who
     * looks for this exact expression there will not find it.</p>
     *
     * <p><strong>Returns null rather than throwing on a failed lookup.</strong> This is a Mongo query running after
     * the membership is already saved, so a database hiccup here must cost the announcement and not the
     * subscription.</p>
     */
    private String patientEmail(String patientId) {
        if (patientId == null) {
            return null;
        }
        try {
            return lookUpPatientEmail(patientId);
        } catch (Exception e) {
            log.warn("Could not resolve an email for patient {} — the membership is unaffected", patientId, e);
            return null;
        }
    }

    private String lookUpPatientEmail(String patientId) {
        return profileRepository
            .findByPatientId(patientId)
            .stream()
            .findFirst()
            .or(() -> profileRepository.findById(patientId))
            .map(Profile::getEmail)
            .orElse(null);
    }
}
