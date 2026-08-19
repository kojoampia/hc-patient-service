package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.OnboardingService;
import net.jojoaddison.service.dto.OnboardingBaselineDTO;
import net.jojoaddison.service.dto.OnboardingCareAngelDTO;
import net.jojoaddison.service.dto.OnboardingCurrentStateDTO;
import net.jojoaddison.service.dto.OnboardingIdentificationDTO;
import net.jojoaddison.service.dto.OnboardingIdentityDTO;
import net.jojoaddison.service.dto.OnboardingStatusDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * The patient's way into their own record.
 *
 * <h2>Every endpoint here acts on the token's email, and never on the payload</h2>
 *
 * <p>That is the whole security design in one sentence. The step payloads are DTOs rather than {@code Profile}
 * documents precisely so that {@code email}, {@code patientId} and {@code id} are not fields a caller can set — a
 * request body that could name the patient would let anyone onboard on somebody else's behalf, or attach themselves to
 * an existing record.</p>
 *
 * <h2>A note on the step paths</h2>
 *
 * <p>The plan described these as {@code PATCH /api/onboarding/step/{n}}. They are named instead — {@code /care-angel},
 * {@code /baseline} and so on — because the steps carry genuinely different payloads, and one handler taking five
 * shapes can only be typed as a map or as a wrapper with five optional blocks. Both make the contract harder for the
 * mobile client to implement against, which is the opposite of what freezing it was for. The step <em>numbers</em>
 * survive in {@link OnboardingStatusDTO#step()}, which is what a resuming client actually reads.</p>
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingResource {

    private static final String ENTITY_NAME = "onboarding";

    private final Logger log = LoggerFactory.getLogger(OnboardingResource.class);

    private final OnboardingService onboardingService;
    private final PatientScope patientScope;

    public OnboardingResource(OnboardingService onboardingService, PatientScope patientScope) {
        this.onboardingService = onboardingService;
        this.patientScope = patientScope;
    }

    /**
     * {@code GET /api/onboarding/status} : where this patient is in the journey.
     *
     * <p>The portal guard's single call. It answers for a caller with no record at all, which is what makes it usable
     * before anything else exists.</p>
     */
    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusDTO> status() {
        return ResponseEntity.ok(onboardingService.status(patientScope.bootstrapEmail()));
    }

    /**
     * {@code POST /api/onboarding} : step 1, and the one write that may run before a profile exists.
     *
     * @return 201 with the new profile.
     * @throws BadRequestAlertException 409-equivalent if this account already has a record.
     */
    @PostMapping("")
    public ResponseEntity<Profile> start(@RequestBody OnboardingIdentityDTO identity) throws URISyntaxException {
        String email = patientScope.bootstrapEmail();
        refuseAngels();
        log.debug("REST request to start onboarding");

        Profile profile = onboardingService.start(email, identity);
        return ResponseEntity.created(new URI("/api/profiles/" + profile.getId())).body(profile);
    }

    /** {@code PATCH /api/onboarding/care-angel} : step 2. Completes on nomination, not on acceptance. */
    @PatchMapping(value = "/care-angel", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Profile> careAngel(@RequestBody OnboardingCareAngelDTO careAngel) {
        return ResponseEntity.ok(onboardingService.careAngel(own(), careAngel));
    }

    /** {@code PATCH /api/onboarding/baseline} : step 3. */
    @PatchMapping(value = "/baseline", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Profile> baseline(@RequestBody OnboardingBaselineDTO baseline) {
        return ResponseEntity.ok(onboardingService.baseline(own(), baseline));
    }

    /** {@code PATCH /api/onboarding/current-state} : step 4. */
    @PatchMapping(value = "/current-state", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Profile> currentState(@RequestBody OnboardingCurrentStateDTO state) {
        return ResponseEntity.ok(onboardingService.currentState(own(), state));
    }

    /** {@code PATCH /api/onboarding/identification} : step 5. Required, and with no "none". */
    @PatchMapping(value = "/identification", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Profile> identification(@RequestBody OnboardingIdentificationDTO identification) {
        return ResponseEntity.ok(onboardingService.identification(own(), identification));
    }

    /** {@code POST /api/onboarding/complete} : finish, if every required answer is actually there. */
    @PostMapping("/complete")
    public ResponseEntity<Profile> complete() {
        return ResponseEntity.ok(onboardingService.complete(own()));
    }

    /**
     * The caller's own profile, for the steps that need one to exist already.
     *
     * <p>Resolved from the token's email rather than through {@code currentPatientId()}, because an angel acting for
     * somebody else must not land in that patient's onboarding — see {@link #refuseAngels()}.</p>
     */
    private Profile own() {
        String email = patientScope.bootstrapEmail();
        refuseAngels();
        return onboardingService
            .profileFor(email)
            .orElseThrow(() -> new BadRequestAlertException("Onboarding has not been started", ENTITY_NAME, "notstarted"));
    }

    /**
     * An angel may act as the patient for everything except this.
     *
     * <p>Onboarding is the patient answering questions about themselves, and the consent it collects — nominating
     * someone to act for them, authorising a clinician to activate a standby — cannot be given by the person who
     * benefits from it. An angel who is also a patient onboards themselves by not sending the header.</p>
     */
    private void refuseAngels() {
        if (patientScope.isActingAsAngel()) {
            throw new AccessDeniedException("A care angel cannot run onboarding on the patient's behalf");
        }
    }
}
