package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.MembershipService;
import net.jojoaddison.service.event.PatientEventType;
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
 *
 * <h2>Nor does the back office erase one by not mentioning it</h2>
 *
 * <p>The same three fields — {@code status}, {@code memberNumber}, {@code renewalDate} — were guarded against the
 * subscriber <em>choosing</em> them and not against an administrator <em>clearing</em> them. Both guards handed an
 * administrator back whatever the body carried, {@code PUT} replaces the document wholesale, and the generated update
 * form makes a null status one click; the record was wiped in this service with nothing to restore it from. Since
 * 2026-09-09 all three are carried over from the stored document when the request does not name them, whoever the
 * caller is. Backlog item 30, and {@link #termForUpdate} holds the whole rule and the price it charges.</p>
 *
 * <h2>Choosing a plan says so on {@code patient-events}</h2>
 *
 * <p>Until 2026-09-08 a patient chose a tier, a {@code PENDING} membership was written, and <em>nothing told
 * anybody</em> — so the request sat until somebody in the back office happened to look. Choosing a plan now publishes
 * {@link PatientEventType#PLAN_CHOSEN}, which hc-admin consumes and raises for action.</p>
 *
 * <p><strong>Here rather than in a client.</strong> {@code web} and {@code mobile} each have their own
 * {@code choosePlan} and both come through this class; a browser-side publish would miss the app, miss any future
 * caller, and put a broker on the far side of a CSP.</p>
 *
 * <p><strong>And not in this class either.</strong> Announcing lives in {@link MembershipService}, which owns every
 * write to a {@code Membership} — this resource holds the HTTP contract and the guards on what a caller may decide,
 * and nothing about the wire beyond them. See that class for why the seam exists at all.</p>
 */
@RestController
@RequestMapping("/api/memberships")
public class MembershipResource {

    private final Logger log = LoggerFactory.getLogger(MembershipResource.class);

    private static final String ENTITY_NAME = "patientMsMembership";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MembershipService membershipService;

    private final MembershipRepository membershipRepository;

    private final PatientScope patientScope;

    public MembershipResource(MembershipService membershipService, MembershipRepository membershipRepository, PatientScope patientScope) {
        this.membershipService = membershipService;
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
        // And a subscriber does not issue their own membership number or set their own renewal date, for the reason
        // statusOnCreate exists: a value a client may choose is a claim, not a record. Both are back-office
        // assignments — neither client's choosePlan sends them — so for anyone but an administrator they come off the
        // wire here. Found by review 2026-09-08, when a javadoc claimed this guard already existed.
        if (!mayDecideStatus()) {
            membership.setMemberNumber(null);
            membership.setRenewalDate(null);
        }
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        membership.setCreatedBy(AuditStamp.currentUser());
        membership.setCreatedDate(AuditStamp.today());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());
        Membership result = membershipService.save(membership);
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
        // And neither are the terms of the subscription — see termForUpdate. POST has stripped these since
        // 2026-09-08 and these two verbs did not, which is the drift this class's own javadoc warns about twice.
        membership.setMemberNumber(termForUpdate(existing.getMemberNumber(), membership.getMemberNumber()));
        membership.setRenewalDate(termForUpdate(existing.getRenewalDate(), membership.getRenewalDate()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        membership.setCreatedBy(existing.getCreatedBy());
        membership.setCreatedDate(existing.getCreatedDate());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());

        // The status the stored document held, so the service can tell an approval from a rename. Read from the
        // record loaded above rather than from the body: under the write guard a non-administrator's requested status
        // is discarded and the stored one carried over, so the body and the persisted value routinely differ for a
        // caller who changed nothing.
        Membership result = membershipService.update(membership, existing.getStatus());
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
        // And neither are the terms of the subscription — see termForUpdate. POST has stripped these since
        // 2026-09-08 and these two verbs did not, which is the drift this class's own javadoc warns about twice.
        membership.setMemberNumber(termForUpdate(existing.getMemberNumber(), membership.getMemberNumber()));
        membership.setRenewalDate(termForUpdate(existing.getRenewalDate(), membership.getRenewalDate()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        membership.setCreatedBy(existing.getCreatedBy());
        membership.setCreatedDate(existing.getCreatedDate());
        membership.setModifiedBy(AuditStamp.currentUser());
        membership.setModifiedDate(AuditStamp.today());

        // The held status, for the reason given on the PUT above.
        Optional<Membership> result = membershipService.partialUpdate(membership, existing.getStatus());

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
    private MembershipStatus statusOnCreate(MembershipStatus requestedStatus) {
        return mayDecideStatus() ? requestedStatus : MembershipStatus.PENDING;
    }

    /**
     * The status an update must keep, which for anybody but an administrator is the stored one.
     *
     * <p>Carried over exactly as {@code createdBy} and {@code createdDate} are, and for the same reason: it is a
     * record of a decision somebody else made. Passing the stored value back rather than refusing the request keeps
     * {@code PUT} and {@code PATCH} working for the fields a patient <em>may</em> edit — a subscriber renaming their
     * membership should not get a 403 because the payload also echoed the status the server sent them.</p>
     *
     * <p><strong>And an administrator cannot clear it by omission either</strong>, which until 2026-09-09 they could:
     * this returned the requested status verbatim for an administrator, {@code PUT} replaces the document wholesale,
     * and the generated update form's status {@code <select>} carries a blank {@code <option [ngValue]="null">} — so
     * saving that form persisted a null. A null is in no {@code MembershipStatus} constant, hc-admin cannot filter on
     * it, and both clients pick the held plan with {@code status?.toUpperCase() === 'ACTIVE'}, which falls through to
     * the first membership in the list rather than to none. Backlog item 30; the rule is
     * {@link #termForUpdate}'s and this delegates to it so that the two cannot drift apart again.</p>
     *
     * @param storedStatus the status currently recorded on the stored document.
     * @param requestedStatus the status in the request body.
     * @return the value to persist.
     */
    private MembershipStatus statusForUpdate(MembershipStatus storedStatus, MembershipStatus requestedStatus) {
        return termForUpdate(storedStatus, requestedStatus);
    }

    /**
     * A value on the membership that only the back office decides, which an update keeps unless the back office said
     * otherwise. The one carry-over rule on this resource: {@code status}, {@code memberNumber} and
     * {@code renewalDate} all come through here.
     *
     * <p>{@code memberNumber} and {@code renewalDate} are back-office assignments for the reason {@code statusOnCreate}
     * gives: a value a client may choose is a claim, not a record, and a self-chosen renewal date is a year of care
     * nobody sold them. {@code POST} has stripped both from a non-administrator since 2026-09-08 and <strong>{@code
     * PUT} and {@code PATCH} did not</strong> — so a patient could not issue themselves a membership number at
     * creation and could issue themselves one a second later. Found by review of backlog item 27; the javadoc on
     * {@link PatientEventType#PLAN_CHOSEN} had been asserting the guard held on every verb, which is the same
     * defect-shape as the missing guard item 18's review found behind a javadoc that had been written before it.</p>
     *
     * <p><strong>Carried over rather than nulled, which is not what {@code POST} does and must not be.</strong>
     * {@code POST} strips because there is nothing to preserve. Here there is: {@code PUT} replaces the document
     * wholesale, so nulling would mean a patient renaming their membership <em>erases</em> the number and renewal date
     * an administrator assigned — trading a privilege escalation for silent data loss. On {@code PATCH} it is also the
     * right merge behaviour: the merge copies non-null fields only, so handing back the stored value writes it over
     * itself and changes nothing.</p>
     *
     * <h3>The administrator had the hole this closed, and the javadoc above read as though they did not</h3>
     *
     * <p>Everything above is the {@code false} branch of {@link #mayDecideStatus()}. <strong>An administrator took the
     * other branch and got the requested value verbatim — including null</strong> — so the identical wipe this method
     * was written to prevent happened to them by omitting a field instead of by renaming a membership, and
     * {@code PUT} replacing wholesale did the rest. That is the defect-shape review named twice while closing backlog
     * item 27: <em>a javadoc asserting a guard that holds on one path and is read as holding on all of them.</em>
     * Backlog item 30, and the third time in this file a rule written for one caller has been wrong for the next.</p>
     *
     * <p><strong>Carry-over, not refusal, and the deciding argument is not symmetry with the neighbours.</strong>
     * Refusing an administrator's clearing {@code PUT} with a 400 would have to be written at the {@code PUT} call
     * site — {@code PATCH} cannot refuse an absent field, because an absent field is what {@code PATCH} is for. That
     * is one rule in one of two places on a resource whose two update verbs have now drifted apart three times.
     * Carry-over fits in the shared helper, so both verbs get it whether or not anyone remembers. It also matches
     * what {@code patientId}, {@code createdBy} and {@code createdDate} already do four lines away, and a resource
     * that silently carries four fields over and answers 400 on three others is one nobody can predict.</p>
     *
     * <p><strong>What it costs, stated rather than discovered: an administrator can no longer blank any of the three
     * by omitting it.</strong> Deliberately — an omitted field is an accident and a sent one is a statement — but the
     * three do not have equally good replacements and that is worth knowing before somebody needs one:</p>
     *
     * <ul>
     *   <li>{@code status} — no loss. {@code PENDING} exists to say "not decided", and {@code CANCELLED} to say a
     *       membership ended. Neither was ever expressible as a null.</li>
     *   <li>{@code memberNumber} — an empty string still clears it, since only null carries over. Explicit, which is
     *       the point. Note it stores {@code ""} rather than null, and {@code web} renders that as an empty cell
     *       where it renders a true null as {@code —}.</li>
     *   <li>{@code renewalDate} — <strong>no route through this API at all.</strong> It is a {@code LocalDate}, so
     *       there is no empty spelling of it. Correcting a wrong date still works — send the right one — but blanking
     *       one is now impossible short of a write outside this service. Judged an acceptable cost rather than
     *       overlooked: a membership that should carry no renewal date is one that was never sold, which is what
     *       {@code CANCELLED} records. If a real need for the blank appears it wants its own explicit route, not the
     *       silent one this closes.</li>
     * </ul>
     *
     * @param storedTerm the value on the stored document.
     * @param requestedTerm the value in the request body.
     * @return the value to persist.
     */
    private <T> T termForUpdate(T storedTerm, T requestedTerm) {
        if (!mayDecideStatus()) {
            return storedTerm;
        }
        // An administrator decides these; they do not clear them by leaving them out of a PUT body. Two questions,
        // deliberately answered separately: may this caller decide, and did they actually say anything.
        return requestedTerm == null ? storedTerm : requestedTerm;
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
