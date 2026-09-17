package net.jojoaddison.web.rest;

import net.jojoaddison.domain.Profile;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.ResponseUtil;

/**
 * {@code GET /api/profile/{accountId}} — how the rest of the estate reads identity out of this subsystem.
 *
 * <p>Backlog item 44, and the architect's words are the whole contract: <em>"Account is accessed over
 * {@code [repo]-gateway/api/admin/users}, profile is accessed over {@code [repo]-service/api/profile/:accountId}. No
 * need for any extra specific api shapes."</em> Two plain reads, the same in every product, and no composed endpoint
 * per consumer — hc-professional had built one for hc-admin and it was cancelled unmerged.</p>
 *
 * <h2>Singular, and a second controller, and both are deliberate</h2>
 *
 * <p>{@code /api/profiles/{id}} already means <em>the profile's own id</em>, so {@code /api/profiles/{accountId}}
 * cannot be added — the two would collide on one pattern, which is why hc-professional reached for
 * {@code /profiles/account/{accountId}}. The singular path removes the ambiguity instead of routing around it.
 * <strong>Do not "tidy" it to plural.</strong> It needs a class of its own only because a class-level
 * {@code @RequestMapping} is a prefix and there is no way to escape one from a method; {@link ProfileResource} keeps
 * every read it has, and {@code /api/profiles/email/{email}} in particular stays — both clients bootstrap on it — as
 * a convenience read rather than an integration contract.</p>
 *
 * <h2>{@code ROLE_ADMIN}, because the path names a subject</h2>
 *
 * <p>The estate rule: an endpoint that names a subject in its path is administrative; only an endpoint that cannot
 * name anyone but the caller may be merely authenticated. Identity is not a boundary here — the path is — so
 * authentication alone would constrain nothing.</p>
 *
 * <p><strong>That matters more in this estate than in most, because the three gateways share one signing key.</strong>
 * "Any authenticated caller" is not this product's users: it is every account in hc-patient, hc-admin and
 * hc-professional, including every clinician and every role-less applicant. hc-professional's own
 * {@code ProfileResource} carried no authority check at all and fell through to {@code /api/** -> authenticated()},
 * so a carer could read a doctor's twenty-one-field profile including card number, birth date and address. This is
 * the same document one identifier over.</p>
 *
 * <p><strong>There is deliberately no self-carve-out.</strong> A patient reading their own record already has
 * {@code /api/profiles/email/{email}}, which is scoped to the token's own address and cannot name anyone else. Adding
 * "…or the caller's own account id" here would mean comparing caller against path, which is the shape that gets it
 * wrong; the caller-scoped read exists so that comparison never has to be written.</p>
 *
 * <p>The payload is the plain {@code Profile}. Item 44 is explicit that <em>"no extra specific api shapes"</em> means
 * no projection composed for one consumer — a caller reads the account from the gateway, reads this, and composes
 * what it needs itself.</p>
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileByAccountResource {

    private final Logger log = LoggerFactory.getLogger(ProfileByAccountResource.class);

    private final ProfileService profileService;

    public ProfileByAccountResource(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * {@code GET  /api/profile/:accountId} : the profile belonging to a gateway account.
     *
     * <p>Not filtered through {@code PatientScope}: only {@code ROLE_ADMIN} reaches this method, and an administrator
     * is unrestricted there anyway — an ownership filter on top of the authority check would be a second expression of
     * one rule, which is how a guard becomes untestable. The single-record reads on {@link ProfileResource} keep
     * theirs, because those are open to callers who are not administrators.</p>
     *
     * @param accountId the patient gateway's {@code User.id}.
     * @return {@code 200 (OK)} with the profile, or {@code 404 (Not Found)} when this subsystem holds none for that
     *         account — which is an ordinary answer, not a failure: a gateway account can exist with no patient
     *         record behind it, and every clinician signing in through a sibling product is one.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @GetMapping("/{accountId}")
    public ResponseEntity<Profile> getProfileByAccountId(@PathVariable("accountId") String accountId) {
        log.debug("REST request to get Profile by accountId : {}", accountId);
        return ResponseUtil.wrapOrNotFound(profileService.findByAccountId(accountId));
    }
}
