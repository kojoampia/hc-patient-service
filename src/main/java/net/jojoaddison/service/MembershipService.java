package net.jojoaddison.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
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
     * Save a membership, and say so.
     *
     * <p>Saved first, then announced. The event is a notification, never the mechanism — see
     * {@link #announceChosenPlan}.</p>
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
     * Update a membership.
     *
     * @param membership the entity to save.
     * @return the persisted entity.
     */
    public Membership update(Membership membership) {
        log.debug("Request to update Membership : {}", membership);
        return membershipRepository.save(membership);
    }

    /**
     * Partially update a membership, merging only the fields the request carried.
     *
     * @param membership the entity to update partially.
     * @return the persisted entity, or empty when there is no such membership.
     */
    public Optional<Membership> partialUpdate(Membership membership) {
        log.debug("Request to partially update Membership : {}", membership);

        return membershipRepository
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
     * literal {@code PENDING}, because an administrator may legitimately have created an {@code ACTIVE} one, and an
     * event should report what was written. The field names are the honest ones for this document: backlog item 18
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
        data.put("membershipId", membership.getId());
        data.put("planCode", membership.getPlan());
        data.put("planName", membership.getName());
        // The name, not the enum: the wire shape should not move if the enum's serialization ever does.
        data.put("status", membership.getStatus() == null ? null : membership.getStatus().name());
        events.publish(PatientEventType.PLAN_CHOSEN, patientEmail(membership.getPatientId()), null, membership.getPatientId(), data);
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
