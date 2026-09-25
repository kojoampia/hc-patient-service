package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DelegationParty;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.event.EntityEventPublisher;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * The care-delegation state machine.
 *
 * <p>Every transition lives here rather than in the resource, and the resource exposes one endpoint per transition
 * rather than a generic update. That is the security model, not a style preference: a generic
 * {@code PATCH /api/care-delegations/{id}} would let an angel set their own status to {@code ACTIVE}, which is the
 * entire arrangement defeated in one verb.</p>
 *
 * <pre>
 *   STANDBY ──requestActivation──▶ AWAITING_COUNTERSIGNATURE ──countersign──▶ PENDING ──accept──▶ ACTIVE
 *                                   (professional A)          (professional B, B≠A)   (the angel)
 *
 *   PENDING ──decline──▶ DECLINED
 *   any pre-terminal state ──revoke──▶ REVOKED
 * </pre>
 *
 * <p>Nothing is deleted. Ending a delegation records who ended it and when, and re-nominating the same person creates a
 * new row rather than reopening an old one.</p>
 */
@Service
public class CareDelegationService {

    private static final String ENTITY_NAME = "careDelegation";

    private final Logger log = LoggerFactory.getLogger(CareDelegationService.class);

    private final CareDelegationRepository careDelegationRepository;
    private final ProfileRepository profileRepository;
    private final PatientEventPublisher events;

    /**
     * The second channel, since backlog item 46 — the same change, on {@code patient.event}.
     *
     * <p>Not a replacement for {@code events}: both are published on every transition so the gateway's mail router can
     * be rebound to one channel per product without a window in which nobody is told. Removing the old one is the end
     * of item 46 and belongs to the consumer side first.</p>
     */
    private final EntityEventPublisher entityEvents;

    public CareDelegationService(
        CareDelegationRepository careDelegationRepository,
        ProfileRepository profileRepository,
        PatientEventPublisher events,
        EntityEventPublisher entityEvents
    ) {
        this.careDelegationRepository = careDelegationRepository;
        this.profileRepository = profileRepository;
        this.events = events;
        this.entityEvents = entityEvents;
    }

    /**
     * Nominate a care angel. The delegation starts {@code PENDING} and confers nothing until they accept.
     *
     * @param patientId the patient nominating.
     * @param patientEmail the nominating patient's own email, so self-nomination can be refused.
     * @param angelEmail the nominee.
     * @param angelName the nominee's name, for display.
     * @param angelPhone the nominee's phone.
     * @return the new delegation.
     */
    public CareDelegation nominate(String patientId, String patientEmail, String angelEmail, String angelName, String angelPhone) {
        log.debug("Request to nominate a care angel for patient {}", patientId);
        rejectSelfNomination(patientEmail, angelEmail);
        return careDelegationRepository.save(
            stamp(
                new CareDelegation()
                    .patientId(patientId)
                    .angelEmail(normalise(angelEmail))
                    .angelName(angelName)
                    .angelPhone(angelPhone)
                    .status(DelegationStatus.PENDING)
                    .grantedAt(Instant.now())
            )
        );
    }

    /**
     * Record a standby nominee: dormant, consented to in advance, and conferring nothing at all.
     *
     * <p>No account is created and no mail is sent. Inviting somebody who may never be activated would be both
     * intrusive and wasteful; until a clinician ripens this row it is a contact detail, not a nomination.</p>
     *
     * @param advanceConsent the patient's recorded authorisation. Without it there is nothing to record.
     * @return the dormant delegation.
     */
    public CareDelegation recordStandby(
        String patientId,
        String patientEmail,
        String angelEmail,
        String angelName,
        String angelPhone,
        boolean advanceConsent
    ) {
        log.debug("Request to record a standby nominee for patient {}", patientId);
        rejectSelfNomination(patientEmail, angelEmail);
        if (!advanceConsent) {
            // The consent is the authorisation. Storing the nominee without it would leave a row that looks
            // activatable and is not, and the difference would only surface at the worst possible moment.
            throw new DomainStateException("A standby nominee cannot be recorded without advance consent", ENTITY_NAME, "consentrequired");
        }
        return careDelegationRepository.save(
            stamp(
                new CareDelegation()
                    .patientId(patientId)
                    .angelEmail(normalise(angelEmail))
                    .angelName(angelName)
                    .angelPhone(angelPhone)
                    .status(DelegationStatus.STANDBY)
                    .advanceConsent(true)
                    .grantedAt(Instant.now())
            )
        );
    }

    /**
     * The angel accepts. This is the only transition into {@code ACTIVE}, and the only one that grants access.
     *
     * @param id the delegation.
     * @param callerEmail the token's email.
     * @return the active delegation.
     */
    public CareDelegation accept(String id, String callerEmail) {
        CareDelegation delegation = require(id);
        requireStatus(delegation, DelegationStatus.PENDING);
        requireAngel(delegation, callerEmail);

        delegation.setStatus(DelegationStatus.ACTIVE);
        delegation.setAcceptedAt(Instant.now());
        CareDelegation saved = careDelegationRepository.save(stamp(delegation));
        refreshProfileCache(saved);
        return saved;
    }

    /**
     * The nominee declines. Terminal — a later change of heart is a fresh nomination.
     */
    public CareDelegation decline(String id, String callerEmail) {
        CareDelegation delegation = require(id);
        requireStatus(delegation, DelegationStatus.PENDING);
        requireAngel(delegation, callerEmail);

        delegation.setStatus(DelegationStatus.DECLINED);
        return careDelegationRepository.save(stamp(delegation));
    }

    /**
     * Either party ends the delegation.
     *
     * <p>The patient may revoke from any pre-terminal state — including while a countersignature is outstanding,
     * because a patient lucid enough to object is lucid enough not to need a standby ripened for them.</p>
     *
     * @param id the delegation.
     * @param callerEmail the token's email.
     * @return the revoked delegation, carrying which side ended it.
     */
    public CareDelegation revoke(String id, String callerEmail) {
        CareDelegation delegation = require(id);
        if (isTerminal(delegation.getStatus())) {
            throw new DomainStateException("This delegation has already ended", ENTITY_NAME, "alreadyended");
        }
        DelegationParty party = partyFor(delegation, callerEmail);

        boolean wasActive = delegation.isActive();
        delegation.setStatus(DelegationStatus.REVOKED);
        delegation.setRevokedAt(Instant.now());
        delegation.setRevokedBy(party);
        CareDelegation saved = careDelegationRepository.save(stamp(delegation));
        if (wasActive) {
            clearProfileCache(saved);
        }
        // An angel stepping down is the case the patient must hear about: they are left without one, and only they can
        // nominate a replacement. A patient revoking is told nothing they do not already know.
        publishChange(saved, party == DelegationParty.ANGEL ? "REVOKED_BY_ANGEL" : "REVOKED_BY_PATIENT", Map.of());
        return saved;
    }

    /**
     * A professional declares the patient incapacitated, moving a standby nomination one step along.
     *
     * <p>This does not grant anything. It records the declaration and waits for a second professional.</p>
     *
     * @param id the delegation.
     * @param professionalId the declaring professional.
     * @param reason the incapacity declaration, stored rather than merely logged.
     */
    public CareDelegation requestActivation(String id, String professionalId, String reason) {
        CareDelegation delegation = require(id);
        requireStatus(delegation, DelegationStatus.STANDBY);
        if (!Boolean.TRUE.equals(delegation.getAdvanceConsent())) {
            // The only evidence the patient ever agreed to this. Without it, activating would be a clinician granting
            // access to a medical record on their own authority, which is exactly what the standby path avoids.
            throw new DomainStateException("This nominee was recorded without advance consent", ENTITY_NAME, "noconsent");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainStateException("An incapacity declaration must say why", ENTITY_NAME, "reasonrequired");
        }

        delegation.setStatus(DelegationStatus.AWAITING_COUNTERSIGNATURE);
        delegation.setActivationRequestedById(professionalId);
        delegation.setActivationRequestedAt(Instant.now());
        delegation.setActivationReason(reason.trim());
        return careDelegationRepository.save(stamp(delegation));
    }

    /**
     * A second professional countersigns, and the nomination becomes a real one the nominee may accept.
     *
     * @param id the delegation.
     * @param professionalId the countersigning professional. <strong>Must differ from the requester.</strong>
     */
    public CareDelegation countersign(String id, String professionalId) {
        CareDelegation delegation = require(id);
        requireStatus(delegation, DelegationStatus.AWAITING_COUNTERSIGNATURE);

        // This comparison is the whole of the two-signature control. Everything else on the standby path is
        // bookkeeping; if this is wrong, one clinician can ripen a delegation alone and the second signature is
        // decorative.
        if (professionalId != null && professionalId.equals(delegation.getActivationRequestedById())) {
            throw new DomainStateException(
                "The professional who declared the incapacity cannot also countersign it",
                ENTITY_NAME,
                "samesignatory"
            );
        }

        delegation.setStatus(DelegationStatus.PENDING);
        delegation.setCountersignedById(professionalId);
        delegation.setCountersignedAt(Instant.now());
        CareDelegation saved = careDelegationRepository.save(stamp(delegation));
        publishChange(
            saved,
            "STANDBY_ACTIVATED",
            Map.of(
                "activationRequestedById",
                String.valueOf(saved.getActivationRequestedById()),
                "countersignedById",
                String.valueOf(saved.getCountersignedById())
            )
        );
        return saved;
    }

    /** Every delegation naming this caller as the angel, in any state — what the sign-in profile picker reads. */
    public List<CareDelegation> delegationsWhereAngel(String email) {
        return careDelegationRepository.findByAngelEmailIgnoreCase(normalise(email));
    }

    /** Every delegation over this patient's record — what the portal's delegation screen reads. */
    public List<CareDelegation> delegationsForPatient(String patientId) {
        return careDelegationRepository.findByPatientId(patientId);
    }

    public Optional<CareDelegation> findOne(String id) {
        return careDelegationRepository.findById(id);
    }

    /**
     * Announces a delegation change on the shared patient stream.
     *
     * <p>Keyed on the patient's email rather than the angel's, so a delegation change sorts into the same partition as
     * that patient's onboarding and account events. The angel's address travels in the payload because the gateway's
     * consumer has to write to them, and it is a contact detail rather than anything clinical.</p>
     *
     * <h2>Announced on both channels since backlog item 46, and this is the add of add-before-removing</h2>
     *
     * <p>The {@code patient-events} frame below is <strong>unchanged, byte for byte</strong> — hc-admin's consumer and
     * the gateway's mail router both still read it. The second call puts the same transition on {@code patient.event},
     * which is the channel the estate is consolidating on. Two publishes rather than one, deliberately and temporarily:
     * a producer writing where nobody reads is harmless, and the reverse is silence that looks like health.</p>
     *
     * <p>⛔ <strong>The gateway half is NOT a rebind on its own, and this comment said it was.</strong> The two frames
     * carry the same <em>values</em> and put the patient's address in <em>different places</em>: {@code subject.email}
     * on {@code patient-events}, {@code data.patientEmail} here — because item 124 settled that this channel's
     * {@code subject} is the record. {@code CareDelegationMailer} reads {@code event.subject().email()}, and the
     * gateway's {@code PatientEvent.Subject} carries {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so pointing
     * the binding at this topic <strong>does not fail — it binds and yields a null recipient</strong>, and the mail
     * simply stops with a DEBUG line. So the gateway must move the handler to {@code data.patientEmail} <em>in the same
     * commit as the rebind</em>.</p>
     *
     * <p>⚠ <strong>The new frame is narrower than the old one, on purpose.</strong> It carries the transition and the
     * two addresses — measured to be the whole of what the gateway's {@code CareDelegationMailer} reads — and not
     * {@code delegationId} (which <em>is</em> {@code subject.entityId} there, and carrying an id twice is how two copies
     * of it come to disagree) nor the two signatory ids from {@code extra} (which no consumer reads, and which are
     * recorded on the document and on this same channel's {@code EntityChanged} frame for the save). Widening it later
     * costs nothing; a field on a channel that nobody needs cannot be taken back.</p>
     */
    private void publishChange(CareDelegation delegation, String change, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("delegationId", delegation.getId());
        data.put("change", change);
        data.put("angelEmail", delegation.getAngelEmail());
        // One profile read serves both subject fields. The subject's third component is the gateway account id
        // since 2026-09-24 — read off the same profile as the email, null when unset or unfound, never the
        // delegation's internal patientId, which no longer travels on this stream.
        Optional<Profile> patient = profileForPatient(delegation.getPatientId());
        String patientEmail = patient.map(Profile::getEmail).orElse(null);
        events.publish(PatientEventType.CARE_DELEGATION_CHANGED, patientEmail, null, patient.map(Profile::getAccountId).orElse(null), data);
        // The same change on patient.event, in that channel's envelope: the delegation is the subject, the values the
        // transition decides are the payload. The one profile read above serves both frames.
        entityEvents.publishCareDelegationChanged(delegation.getId(), change, patientEmail, delegation.getAngelEmail());
    }

    // --- internals ------------------------------------------------------------------------------------------------

    private CareDelegation require(String id) {
        return careDelegationRepository
            .findById(id)
            .orElseThrow(() -> new DomainStateException("Entity not found", ENTITY_NAME, "idnotfound"));
    }

    private void requireStatus(CareDelegation delegation, DelegationStatus expected) {
        if (expected != delegation.getStatus()) {
            throw new DomainStateException("This delegation is not " + expected, ENTITY_NAME, "wrongstatus");
        }
    }

    /** Only the nominee may accept or decline, and only for themselves. */
    private void requireAngel(CareDelegation delegation, String callerEmail) {
        if (!matches(delegation.getAngelEmail(), callerEmail)) {
            throw new AccessDeniedException("Only the nominated care angel can answer this nomination");
        }
    }

    /**
     * Which side of the delegation the caller is, refusing anyone who is neither.
     *
     * <p>A professional or administrator is <em>not</em> a party to somebody's care arrangement. They can ripen a
     * standby the patient consented to, and that is the whole of their say in it.</p>
     */
    private DelegationParty partyFor(CareDelegation delegation, String callerEmail) {
        if (matches(delegation.getAngelEmail(), callerEmail)) {
            return DelegationParty.ANGEL;
        }
        boolean isThePatient = profileRepository
            .findOneByEmailIgnoreCase(callerEmail)
            .map(profile -> Optional.ofNullable(profile.getPatientId()).orElse(profile.getId()))
            .filter(patientId -> patientId.equals(delegation.getPatientId()))
            .isPresent();
        if (isThePatient) {
            return DelegationParty.PATIENT;
        }
        throw new AccessDeniedException("Only the patient or their care angel can end this delegation");
    }

    private void rejectSelfNomination(String patientEmail, String angelEmail) {
        if (matches(patientEmail, angelEmail)) {
            // Nobody is their own angel. Allowing it would create a delegation whose resolution order is undefined —
            // the caller resolves to themselves first, and the row would sit there granting nothing while looking like
            // it granted something.
            throw new DomainStateException("A patient cannot nominate themselves as their own care angel", ENTITY_NAME, "selfnomination");
        }
    }

    /**
     * Keeps {@code Profile.careAngelEmail} in step with the active delegation.
     *
     * <p>Display only. Nothing authorizes on it — see {@link net.jojoaddison.security.PatientScope} — so a failure here
     * makes a screen wrong, never a permission wrong.</p>
     */
    private void refreshProfileCache(CareDelegation delegation) {
        profileForPatient(delegation.getPatientId())
            .ifPresent(profile -> {
                profile.setCareAngelEmail(delegation.getAngelEmail());
                profile.setCareAngelLogin(delegation.getAngelLogin());
                if (delegation.getAngelName() != null) {
                    profile.setCareAngelName(delegation.getAngelName());
                }
                if (delegation.getAngelPhone() != null) {
                    profile.setCareAngelPhone(delegation.getAngelPhone());
                }
                profileRepository.save(profile);
            });
    }

    private void clearProfileCache(CareDelegation delegation) {
        profileForPatient(delegation.getPatientId())
            .ifPresent(profile -> {
                profile.setCareAngelEmail(null);
                profile.setCareAngelLogin(null);
                profileRepository.save(profile);
            });
    }

    /**
     * The profile a patientId names.
     *
     * <p>Falls back to a lookup by id because {@code patientId} was added after some profiles were written, and those
     * carry only their own id — the same fallback {@code PatientScope} applies when resolving the other direction.</p>
     */
    private Optional<Profile> profileForPatient(String patientId) {
        return profileRepository.findByPatientId(patientId).stream().findFirst().or(() -> profileRepository.findById(patientId));
    }

    private CareDelegation stamp(CareDelegation delegation) {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        LocalDate today = LocalDate.now();
        if (delegation.getCreatedDate() == null) {
            delegation.setCreatedDate(today);
            delegation.setCreatedBy(login);
        }
        delegation.setModifiedDate(today);
        delegation.setModifiedBy(login);
        return delegation;
    }

    private static boolean isTerminal(DelegationStatus status) {
        return status == DelegationStatus.REVOKED || status == DelegationStatus.DECLINED;
    }

    private static boolean matches(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
