package net.jojoaddison.service;

import java.util.HashMap;
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
 * 19's inbound {@code patient-events-plan} consumer writes {@code PENDING → ACTIVE} and will live in this package,
 * where it cannot call a private method on a {@code web} class at all. This repo has twice in one week proved that a
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

    public MembershipService(MembershipRepository membershipRepository, ProfileRepository profileRepository, PatientEventPublisher events) {
        this.membershipRepository = membershipRepository;
        this.profileRepository = profileRepository;
        this.events = events;
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
        // clears.
        result.ifPresent(saved -> announceIfDecided(saved, statusHeld));
        return result;
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
