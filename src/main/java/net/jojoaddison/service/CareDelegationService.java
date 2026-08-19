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

    public CareDelegationService(
        CareDelegationRepository careDelegationRepository,
        ProfileRepository profileRepository,
        PatientEventPublisher events
    ) {
        this.careDelegationRepository = careDelegationRepository;
        this.profileRepository = profileRepository;
        this.events = events;
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
     */
    private void publishChange(CareDelegation delegation, String change, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("delegationId", delegation.getId());
        data.put("change", change);
        data.put("angelEmail", delegation.getAngelEmail());
        events.publish(
            PatientEventType.CARE_DELEGATION_CHANGED,
            profileForPatient(delegation.getPatientId()).map(Profile::getEmail).orElse(null),
            null,
            delegation.getPatientId(),
            data
        );
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
