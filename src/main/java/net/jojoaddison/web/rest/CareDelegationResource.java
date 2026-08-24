package net.jojoaddison.web.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.CareDelegationService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for {@link net.jojoaddison.domain.CareDelegation}.
 *
 * <p><strong>There is deliberately no generic CRUD here.</strong> One endpoint per transition, each enforcing who may
 * make it. A generated {@code PATCH /api/care-delegations/{id}} would let an angel set their own status to
 * {@code ACTIVE} — the whole arrangement defeated by the one verb nobody thought about.</p>
 *
 * <p>Nor is there a {@code DELETE}: a delegation records who could act for a patient and between which dates, and that
 * history is the point. Ending one is {@code /revoke}.</p>
 */
@RestController
@RequestMapping("/api/care-delegations")
public class CareDelegationResource {

    private static final String ENTITY_NAME = "careDelegation";

    private final Logger log = LoggerFactory.getLogger(CareDelegationResource.class);

    private final CareDelegationService careDelegationService;
    private final ProfileRepository profileRepository;
    private final PatientScope patientScope;

    public CareDelegationResource(
        CareDelegationService careDelegationService,
        ProfileRepository profileRepository,
        PatientScope patientScope
    ) {
        this.careDelegationService = careDelegationService;
        this.profileRepository = profileRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code GET /api/care-delegations/mine} : everything this caller could act as.
     *
     * <p>The one call the portal makes at sign-in, and what feeds the profile picker: the caller's own profile if they
     * have one, plus every delegation naming them as the angel.</p>
     *
     * <p>It must answer for a caller with <em>no profile at all</em> — an angel who is not themselves a patient is
     * exactly that — so it resolves on the token's email rather than through {@code PatientScope}, which would have
     * nothing to resolve.</p>
     *
     * @return the caller's own patient identity (or null) and their delegations.
     */
    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine() {
        String email = patientScope.bootstrapEmail();
        log.debug("REST request to list what the caller may act as");

        Optional<Profile> own = profileRepository.findOneByEmailIgnoreCase(email);
        List<CareDelegation> delegations = careDelegationService.delegationsWhereAngel(email);

        // Each delegation carries the angel's name, not the patient's — so on its own it cannot tell somebody whose
        // record they would be opening. A picker labelled with opaque ids is precisely the confusion the acting-as
        // banner exists to prevent, so the patient's name is resolved here.
        List<Map<String, Object>> described = delegations
            .stream()
            .map(delegation -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", delegation.getId());
                row.put("patientId", String.valueOf(delegation.getPatientId()));
                row.put("status", String.valueOf(delegation.getStatus()));
                row.put("angelEmail", String.valueOf(delegation.getAngelEmail()));
                row.put("patientName", patientNameOf(delegation.getPatientId()));
                return row;
            })
            .toList();

        return ResponseEntity.ok(
            Map.of(
                "email",
                email,
                "self",
                own
                    .map(profile ->
                        (Object) Map.of(
                            "patientId",
                            Optional.ofNullable(profile.getPatientId()).orElse(profile.getId()),
                            "firstName",
                            Optional.ofNullable(profile.getFirstName()).orElse(""),
                            "lastName",
                            Optional.ofNullable(profile.getLastName()).orElse(""),
                            "onboardingStatus",
                            String.valueOf(profile.getOnboardingStatus())
                        )
                    )
                    .orElse(Map.of()),
                "delegations",
                described
            )
        );
    }

    /**
     * {@code GET /api/care-delegations} : the delegations over the caller's own record.
     *
     * <p>What the portal's delegation screen shows the patient — including a nomination nobody has accepted yet, and a
     * dormant standby.</p>
     */
    @GetMapping("")
    public ResponseEntity<List<CareDelegation>> forCurrentPatient() {
        String patientId = patientScope
            .currentPatientId()
            .orElseThrow(() -> new AccessDeniedException("No patient record is associated with this account"));
        return ResponseEntity.ok(careDelegationService.delegationsForPatient(patientId));
    }

    /**
     * {@code POST /api/care-delegations/:id/accept} : the nominee accepts, and access begins here.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<CareDelegation> accept(@PathVariable("id") String id) {
        log.debug("REST request to accept CareDelegation : {}", id);
        return ResponseEntity.ok(careDelegationService.accept(id, callerEmail()));
    }

    /**
     * {@code POST /api/care-delegations/:id/decline} : the nominee declines. Terminal.
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<CareDelegation> decline(@PathVariable("id") String id) {
        log.debug("REST request to decline CareDelegation : {}", id);
        return ResponseEntity.ok(careDelegationService.decline(id, callerEmail()));
    }

    /**
     * {@code POST /api/care-delegations/:id/revoke} : either party ends it.
     *
     * <p>Which party is worked out from the caller rather than taken from the request — it decides who gets told
     * afterwards, and a client that could choose would be choosing who receives the email.</p>
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<CareDelegation> revoke(@PathVariable("id") String id) {
        log.debug("REST request to revoke CareDelegation : {}", id);
        return ResponseEntity.ok(careDelegationService.revoke(id, callerEmail()));
    }

    /**
     * {@code POST /api/care-delegations/:id/activate} : a doctor declares the patient incapacitated.
     *
     * <p>The first of two signatures. It grants nothing on its own — see {@link #countersign}.</p>
     *
     * <p><strong>{@code ROLE_DOCTOR}, not {@link AuthoritiesConstants#CLINICAL}.</strong> This read
     * {@code ROLE_PROFESSIONAL} until 2026-08-24, and replacing a blanket role with a blanket set would have kept
     * the same defect under a new name. Declaring a patient incapacitated is an assertion about the patient's
     * capacity — a diagnosis, in {@code ScopeOfPractice}'s terms — and that table grants {@code DIAGNOSIS} writes to
     * doctor alone. The second signature is <em>not</em> held to the same standard — see {@link #countersign}, which
     * accepts a nurse as well, because confirming somebody else's assertion is a different act from making it.</p>
     *
     * @param body must carry a {@code reason}: the incapacity declaration, stored rather than merely logged.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.DOCTOR + "')")
    public ResponseEntity<CareDelegation> activate(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        log.debug("REST request to activate standby CareDelegation : {}", id);
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(careDelegationService.requestActivation(id, professionalId(), reason));
    }

    /**
     * {@code POST /api/care-delegations/:id/countersign} : a second professional confirms.
     *
     * <p>Restricted to {@code ROLE_DOCTOR} <strong>or {@code ROLE_NURSE}</strong> and, in the service, to somebody
     * other than the professional who declared the incapacity. Administrators are deliberately <em>not</em> accepted
     * here: the countersignature is a clinical judgement about a patient's capacity, and {@code ROLE_ADMIN} is an
     * operational role.</p>
     *
     * <p><strong>Wider than {@link #activate} on purpose, decided 2026-08-24.</strong> Declaring the incapacity is
     * the assertion — a diagnosis about capacity — and stays with a doctor. Countersigning <em>confirms</em> an
     * assertion somebody else made, which is a different act and one a nurse is competent to make. Requiring two
     * doctors was the stricter reading and the unworkable one: in home healthcare a second doctor is often not
     * reachable, and a delegation that cannot be activated protects nobody.</p>
     *
     * <p>This is the one place a nurse touches a {@code DIAGNOSIS}-adjacent decision, which is why it is a
     * {@code @PreAuthorize} here rather than a row in {@link net.jojoaddison.security.ScopeOfPractice}: that table
     * answers what kind of data a discipline may read and write, and a countersignature is neither. Do not "fix" the
     * apparent inconsistency by granting nurses {@code DIAGNOSIS} writes — that would let them author diagnoses
     * everywhere, which is exactly what the table refuses.</p>
     *
     * <p><strong>The service-side rule that the countersigner differs from the declarer is what makes two signatures
     * mean two people</strong>, and it is untouched by this widening. Without it, widening the role would simply let
     * one person sign twice.</p>
     */
    @PostMapping("/{id}/countersign")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.DOCTOR + "', '" + AuthoritiesConstants.NURSE + "')")
    public ResponseEntity<CareDelegation> countersign(@PathVariable("id") String id) {
        log.debug("REST request to countersign CareDelegation : {}", id);
        return ResponseEntity.ok(careDelegationService.countersign(id, professionalId()));
    }

    /**
     * A patient's display name, for a picker that must never label a medical record with an opaque id.
     *
     * <p>Falls back to the id when there is no profile to read — a delegation can only exist once that patient
     * onboarded, so this should not happen, and showing the id is better than showing nothing.</p>
     */
    private String patientNameOf(String patientId) {
        return profileRepository
            .findByPatientId(patientId)
            .stream()
            .findFirst()
            .or(() -> profileRepository.findById(patientId))
            .map(profile ->
                String
                    .join(
                        " ",
                        Optional.ofNullable(profile.getFirstName()).orElse(""),
                        Optional.ofNullable(profile.getLastName()).orElse("")
                    )
                    .trim()
            )
            .filter(name -> !name.isBlank())
            .orElse(patientId);
    }

    private String callerEmail() {
        return patientScope.bootstrapEmail();
    }

    /**
     * Which professional is signing.
     *
     * <p>The login rather than a {@code Professional} document id: this service has no user management and no reliable
     * mapping from a token to a staff record, and a signature attributed to the wrong person would be worse than one
     * attributed to a login. If that mapping ever exists, this is the single place to change.</p>
     */
    private String professionalId() {
        return SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("The signing professional could not be identified", ENTITY_NAME, "nosignatory")
            );
    }
}
