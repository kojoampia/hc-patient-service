package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Membership}.
 *
 * <h2>A subscriber does not decide whether they are subscribed</h2>
 *
 * <p>Until 2026-09-08 they did. {@code PATCH} copied {@code status} straight from the request body with no authority
 * check at all, and {@code PUT} — which replaces the document wholesale — took it from the wire the same way. Since
 * the lifecycle records approval by moving a membership from {@code PENDING} to {@code ACTIVE}, a patient could
 * approve their own by echoing one word back at the endpoint that had just sent it to them. Nothing else was needed:
 * both verbs already accepted the patient's own record.</p>
 *
 * <p><strong>Overwritten rather than kept off the wire.</strong> The estate's stronger pattern is hc-admin's
 * {@code ProfessionalVerificationRequest}, whose server-stamped fields are <em>"kept off this shape … stronger than
 * overwriting them, because there is nothing on the wire to overwrite"</em>. That is not available here: this service
 * has <em>no DTO layer</em> by documented convention and the domain document is the wire shape, so there is no shape
 * to keep the field off. Overwriting is what {@code patientId}, {@code createdBy} and {@code createdDate} already do
 * in this very class, and matching them is worth more than introducing the only DTO in twenty-three entities. The
 * weakness of the weaker form is worth naming, though: it depends on <em>every</em> write path remembering to call
 * the guard, which is exactly how {@code PUT} came to differ from {@code PATCH} elsewhere.</p>
 */
@RestController
@RequestMapping("/api/memberships")
public class MembershipResource {

    private final Logger log = LoggerFactory.getLogger(MembershipResource.class);

    private static final String ENTITY_NAME = "patientMsMembership";

    /**
     * What a membership a patient creates is worth until somebody in the back office says otherwise.
     *
     * <p>A bare string because {@code Membership.status} is still one. When it becomes a typed vocabulary this is the
     * constant that goes.</p>
     */
    private static final String PENDING_STATUS = "PENDING";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MembershipRepository membershipRepository;

    private final PatientScope patientScope;

    public MembershipResource(MembershipRepository membershipRepository, PatientScope patientScope) {
        this.membershipRepository = membershipRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /memberships} : Create a new membership.
     *
     * @param membership the membership to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new membership, or with status {@code 400 (Bad Request)} if the membership has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Membership> createMembership(@RequestBody Membership membership) throws URISyntaxException {
        log.debug("REST request to save Membership : {}", membership);
        if (membership.getId() != null) {
            throw new BadRequestAlertException("A new membership cannot already have an ID", ENTITY_NAME, "idexists");
        }
        membership.setPatientId(patientScope.requirePatientIdForWrite(membership.getPatientId()));
        // A membership a patient creates is a request, not a subscription — see statusOnCreate.
        membership.setStatus(statusOnCreate(membership.getStatus()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        membership.setCreatedBy(AuditStamp.currentUser());
        membership.setCreatedDate(AuditStamp.today());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());
        Membership result = membershipRepository.save(membership);
        return ResponseEntity
            .created(new URI("/api/memberships/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /memberships/:id} : Updates an existing membership.
     *
     * @param id the id of the membership to save.
     * @param membership the membership to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated membership,
     * or with status {@code 400 (Bad Request)} if the membership is not valid,
     * or with status {@code 500 (Internal Server Error)} if the membership couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Membership> updateMembership(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Membership membership
    ) throws URISyntaxException {
        log.debug("REST request to update Membership : {}, {}", id, membership);
        if (membership.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, membership.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Membership existing = membershipRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        membership.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), membership.getPatientId()));
        // Where the membership stands is the back office's to say, not the subscriber's — see statusForUpdate.
        membership.setStatus(statusForUpdate(existing.getStatus(), membership.getStatus()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        membership.setCreatedBy(existing.getCreatedBy());
        membership.setCreatedDate(existing.getCreatedDate());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());

        Membership result = membershipRepository.save(membership);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, membership.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /memberships/:id} : Partial updates given fields of an existing membership, field will ignore if it is null
     *
     * @param id the id of the membership to save.
     * @param membership the membership to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated membership,
     * or with status {@code 400 (Bad Request)} if the membership is not valid,
     * or with status {@code 404 (Not Found)} if the membership is not found,
     * or with status {@code 500 (Internal Server Error)} if the membership couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Membership> partialUpdateMembership(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Membership membership
    ) throws URISyntaxException {
        log.debug("REST request to partial update Membership partially : {}, {}", id, membership);
        if (membership.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, membership.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Membership existing = membershipRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        membership.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), membership.getPatientId()));
        // Where the membership stands is the back office's to say, not the subscriber's — see statusForUpdate.
        membership.setStatus(statusForUpdate(existing.getStatus(), membership.getStatus()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        membership.setCreatedBy(existing.getCreatedBy());
        membership.setCreatedDate(existing.getCreatedDate());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());

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

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, membership.getId())
        );
    }

    /**
     * The status a membership is created with, which for anybody but an administrator is {@code PENDING}.
     *
     * <p>A patient choosing a plan is making a <em>request</em>. Both clients already post {@code PENDING} —
     * {@code profile.component.ts}'s {@code choosePlan} in {@code web} and its counterpart in {@code mobile} — so
     * this changes nothing they do; what it changes is that they can no longer post anything else. A value a client
     * may choose is a claim rather than a record, which is the same rule {@code source} and the audit fields already
     * follow here.</p>
     *
     * @param requestedStatus the status in the request body.
     * @return the value to persist.
     */
    private String statusOnCreate(String requestedStatus) {
        return mayDecideStatus() ? requestedStatus : PENDING_STATUS;
    }

    /**
     * The status an update must keep, which for anybody but an administrator is the stored one.
     *
     * <p>Carried over exactly as {@code createdBy} and {@code createdDate} are, and for the same reason: it is a
     * record of a decision somebody else made. Passing the stored value back rather than refusing the request keeps
     * {@code PUT} and {@code PATCH} working for the fields a patient <em>may</em> edit — a subscriber renaming their
     * membership should not get a 403 because the payload also echoed the status the server sent them.</p>
     *
     * @param storedStatus the status currently recorded on the stored document.
     * @param requestedStatus the status in the request body.
     * @return the value to persist.
     */
    private String statusForUpdate(String storedStatus, String requestedStatus) {
        return mayDecideStatus() ? requestedStatus : storedStatus;
    }

    /**
     * Whether the caller may say where a membership stands.
     *
     * <p><strong>{@code ROLE_ADMIN} alone</strong>, and deliberately not {@code PatientScope.isUnrestricted()},
     * which also admits the eight clinical disciplines. A membership is a commercial relationship, not a clinical
     * record: a nurse has every business in a patient's medications and none in whether they have paid. This is why
     * the check is written here rather than delegated — {@code PatientScope} answers "whose records" and
     * {@code ScopeOfPractice} answers "what kind of clinical data", and neither question is this one.</p>
     */
    private static boolean mayDecideStatus() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN);
    }

    /**
     * {@code GET  /memberships} : get all the memberships.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of memberships in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<Membership> getAllMemberships(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all Memberships for patient {}", patientId);
        return patientScope.findScoped(patientId, membershipRepository::findAll, membershipRepository::findByPatientId);
    }

    /**
     * {@code GET  /memberships/:id} : get the "id" membership.
     *
     * @param id the id of the membership to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the membership, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Membership> getMembership(@PathVariable("id") String id) {
        log.debug("REST request to get Membership : {}", id);
        Optional<Membership> membership = membershipRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(membership);
    }

    /**
     * {@code DELETE  /memberships/:id} : delete the "id" membership.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the membership to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMembership(@PathVariable("id") String id) {
        log.debug("REST request to delete Membership : {}", id);
        if (membershipRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        membershipRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
