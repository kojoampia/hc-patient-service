package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.DeletionRequestService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Asking to be forgotten, and the administrator's side of answering.
 *
 * <h2>Two audiences, one controller, and the line between them</h2>
 *
 * <p>{@link #raise}, {@link #mine} and {@link #cancel} belong to the patient and are scoped to their own record.
 * {@link #list}, {@link #get}, {@link #complete} and {@link #reject} carry
 * {@code @PreAuthorize(ROLE_ADMIN)}. Nothing a patient can call deletes anything — the most a patient can do is start
 * a clock and stop it again.</p>
 *
 * <p>That split is the request. It also matches what this service already believed: {@code ProfileResource.delete}
 * has been {@code ROLE_ADMIN}-only since patient data became undeletable, and its comment points at "what is meant to
 * replace it". This is that.</p>
 *
 * <h2>Three callers who may not raise one, and why each is refused</h2>
 *
 * <ul>
 *   <li><strong>An angel acting for a patient.</strong> A delegation exists so decisions can be made when the patient
 *   cannot make them — it is not a mandate to end the record. This is the one case worth being loud about: the whole
 *   acting-as design hands somebody else's account full read and write over a record, so a deletion path that
 *   honoured the header would make erasure a thing a delegate could do to a patient.</li>
 *   <li><strong>An administrator or clinician with a patient open.</strong> Unrestricted callers resolve to a scope
 *   through the {@code X-Acting-As} header, so without this check an administrator viewing a record could raise a
 *   deletion request "as" that patient — laundering an administrative decision into a patient's own words. An
 *   administrator who should delete a record can already {@link #complete} one; what they cannot do is manufacture
 *   the consent for it.</li>
 *   <li><strong>An account with no profile.</strong> Nothing to delete, and no patient to attribute it to.</li>
 * </ul>
 *
 * <h2>What completing does not reach</h2>
 *
 * <p>The account itself. This service runs {@code skipUserManagement} and holds no {@code User} document; the login,
 * password and authorities live in the gateway. {@link #complete} erases the record and reports what it removed —
 * closing the account is a second, separate step against the gateway's {@code /api/admin/users/{login}}, by the same
 * administrator. Until that is done the person can still sign in; they will resolve to no patient and see an empty
 * portal, which is correct but is not the same thing as being gone.</p>
 */
@RestController
@RequestMapping("/api/deletion-requests")
public class DeletionRequestResource {

    private final Logger log = LoggerFactory.getLogger(DeletionRequestResource.class);

    private static final String ENTITY_NAME = "patientServiceDeletionRequest";

    private final DeletionRequestService deletionRequestService;
    private final PatientScope patientScope;

    public DeletionRequestResource(DeletionRequestService deletionRequestService, PatientScope patientScope) {
        this.deletionRequestService = deletionRequestService;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /deletion-requests} : ask for this patient's record to be erased.
     *
     * <p>The patient is taken from the caller's own token, never from the body. The body carries at most a reason,
     * which is optional.</p>
     *
     * @param body optionally {@code {"reason": "…"}}.
     * @return {@code 201 (Created)} with the pending request, carrying the date it is owed by.
     */
    @PostMapping("")
    public ResponseEntity<DeletionRequest> raise(@RequestBody(required = false) Map<String, String> body) throws URISyntaxException {
        String patientId = requireOwnPatientScope();

        String reason = body == null ? null : body.get("reason");
        DeletionRequest result = deletionRequestService.raise(
            patientId,
            SecurityUtils.getCurrentUserEmail().orElse(null),
            SecurityUtils.getCurrentUserLogin().orElse(null),
            reason == null || reason.isBlank() ? null : reason.trim()
        );

        log.debug("REST request to raise a DeletionRequest for patient {}", patientId);
        return ResponseEntity.created(new URI("/api/deletion-requests/" + result.getId())).body(result);
    }

    /**
     * {@code GET  /deletion-requests/mine} : this patient's open request, if they have one.
     *
     * <p>{@code 204} rather than {@code 404} when there is none. Having no pending deletion is the ordinary state of
     * every account on the platform, and a client that has to catch an error to discover it will eventually render
     * that error — this endpoint is polled on sign-in by both clients.</p>
     *
     * @return {@code 200 (OK)} with the request, or {@code 204 (No Content)}.
     */
    @GetMapping("/mine")
    public ResponseEntity<DeletionRequest> mine() {
        Optional<String> patientId = patientScope.currentPatientId();
        if (patientId.isEmpty() || patientScope.isActingAsAngel()) {
            // An angel is shown nothing: whether the patient they act for has asked to be erased is between that
            // patient and the platform.
            return ResponseEntity.noContent().build();
        }
        return deletionRequestService
            .pendingFor(patientId.orElseThrow())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * {@code POST  /deletion-requests/:id/cancel} : withdraw a request during the window.
     *
     * <p>The patient's own, and only while it is pending. This is what makes the fourteen days a cooling-off period
     * rather than only a deadline.</p>
     *
     * @param id the request to withdraw.
     * @return {@code 200 (OK)} with it cancelled.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DeletionRequest> cancel(@PathVariable("id") String id) {
        String patientId = requireOwnPatientScope();
        DeletionRequest request = deletionRequestService
            .findOne(id)
            // Ownership before existence, and both raise the same 404: "not yours" must not be distinguishable from
            // "no such id", or this becomes a way to probe for other patients' request ids.
            .filter(current -> patientId.equals(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        return ResponseEntity.ok(deletionRequestService.cancel(request));
    }

    /**
     * {@code GET  /deletion-requests} : the administrator's queue.
     *
     * @param status which requests to list; defaults to {@code PENDING}, which is the queue.
     * @param pageable the pagination. Sort by {@code dueAt} for oldest-deadline-first.
     * @return {@code 200 (OK)} with the page.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @GetMapping("")
    public ResponseEntity<List<DeletionRequest>> list(
        @RequestParam(name = "status", required = false, defaultValue = "PENDING") DeletionRequestStatus status,
        Pageable pageable
    ) {
        log.debug("REST request to list DeletionRequests with status {}", status);
        Page<DeletionRequest> page = deletionRequestService.findByStatus(status, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /deletion-requests/:id} : one request, for the administrator deciding on it.
     *
     * @param id the request.
     * @return {@code 200 (OK)} with it, or {@code 404}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @GetMapping("/{id}")
    public ResponseEntity<DeletionRequest> get(@PathVariable("id") String id) {
        return deletionRequestService.findOne(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code POST  /deletion-requests/:id/complete} : erase the record. <strong>{@code ROLE_ADMIN} only.</strong>
     *
     * <p>This is the delete action, and it is the only one. It removes the patient's documents across every
     * collection this service holds and the report files behind them, then records what it removed. It is
     * irreversible and there is no undo anywhere in this codebase for it.</p>
     *
     * <p>It does <em>not</em> close the gateway account — see the class comment.</p>
     *
     * @param id the request to fulfil.
     * @return {@code 200 (OK)} with it completed, carrying the per-collection counts.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<DeletionRequest> complete(@PathVariable("id") String id) {
        DeletionRequest request = deletionRequestService
            .findOne(id)
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        log.warn("Erasing patient {} on deletion request {}", request.getPatientId(), id);
        return ResponseEntity.ok(deletionRequestService.complete(request, SecurityUtils.getCurrentUserLogin().orElse(null)));
    }

    /**
     * {@code POST  /deletion-requests/:id/reject} : refuse a request, with a reason. {@code ROLE_ADMIN} only.
     *
     * <p>Legitimate reasons exist — a legal hold, an open investigation, a request that is plainly about somebody
     * else's account — and the reason is required so that "no" is never recorded bare.</p>
     *
     * @param id the request to refuse.
     * @param body {@code {"decisionReason": "…"}}, required.
     * @return {@code 200 (OK)} with it rejected.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<DeletionRequest> reject(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        DeletionRequest request = deletionRequestService
            .findOne(id)
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        String reason = body == null ? null : body.get("decisionReason");
        return ResponseEntity.ok(deletionRequestService.reject(request, SecurityUtils.getCurrentUserLogin().orElse(null), reason));
    }

    /**
     * The caller's own patient id, or a refusal.
     *
     * <p>The three refusals are the ones enumerated in the class comment, and they are deliberately one method rather
     * than three checks copied into {@link #raise} and {@link #cancel}: the second copy is where the angel check gets
     * left out.</p>
     *
     * @return the patient id the caller is, not one they may merely see.
     * @throws AccessDeniedException if the caller is a delegate, an unrestricted caller, or resolves to no patient.
     */
    private String requireOwnPatientScope() {
        if (patientScope.isActingAsAngel()) {
            log.warn("Refused a deletion request raised by a care angel acting for another patient");
            throw new AccessDeniedException("A care delegation does not permit asking for the patient's record to be erased");
        }
        if (patientScope.isUnrestricted()) {
            log.warn("Refused a deletion request raised by an unrestricted caller");
            throw new AccessDeniedException("Only the patient may ask for their own record to be erased");
        }
        return patientScope
            .currentPatientId()
            .orElseThrow(() -> new AccessDeniedException("No patient profile is associated with this account"));
    }
}
