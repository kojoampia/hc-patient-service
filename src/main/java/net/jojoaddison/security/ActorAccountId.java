package net.jojoaddison.security;

import java.util.Optional;
import net.jojoaddison.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Who is making the change, expressed as the one identifier the estate agrees on: the gateway's {@code User.id}.
 *
 * <h2>Why the account id and nothing else</h2>
 *
 * <p>Backlog item 45 offered three shapes for the actor on {@code patient.event} — the account id, the login, or no
 * actor at all — and the account id is the one that survives the estate's other rules. hc-admin's item 107 makes
 * {@code account.id = profile.accountId} the join key across all four products, so an account id is the identifier a
 * consumer can already resolve to a person <em>by asking</em>, which is the model the estate is moving to. A login is
 * not that: the login space is small and enumerable, which is the reasoning hc-admin's item 43 uses to treat even a
 * <em>digest</em> of a login as reversible by anybody holding the user list. A login on a cross-product wire is
 * identifying content wearing an identifier's clothes, and this subsystem's whole event design
 * ({@link net.jojoaddison.service.event.PatientEventPublisher}) exists to keep that off the topic.</p>
 *
 * <h2>⚠ This service cannot read an account id out of its own token, and that is the expensive part</h2>
 *
 * <p>The patient gateway mints {@code sub} (the login), {@code email} and {@code auth}, and <strong>no account-id
 * claim</strong> — this repo's backlog item 56 is that gap, and it is the only place that could close it. So the id
 * has to be resolved the long way round: the {@code email} claim, to a {@link net.jojoaddison.domain.Profile}, to
 * that profile's {@code accountId} (backlog item 44's field). One indexed Mongo read.</p>
 *
 * <p><strong>It is cached per request</strong>, in request scope, for the same reason {@link PatientScope} caches its
 * scope there: a single request can write several documents, and an entity-change event is published for every one of
 * them. Without the cache a three-document write is three identical profile lookups. The cache holds the
 * <em>answer</em>, never the email that produced it.</p>
 *
 * <h2>An unresolvable actor is absent, never a fallback</h2>
 *
 * <p>Three real callers resolve to nothing here and all three must produce a frame with no actor rather than a frame
 * with a login in it: an unauthenticated write (a Mongock migration, a startup backfill, the demo seed), a caller
 * whose profile predates the {@code accountId} backfill, and — the one worth naming — <strong>an administrator or
 * clinician from another product</strong>, whose account lives in hc-admin's or hc-professional's gateway and who has
 * no {@code Profile} in this subsystem at all. The three gateways share one signing key, so such a caller
 * authenticates here perfectly well and simply is not anybody this service can name.</p>
 *
 * <p>Falling back to the login in those cases would put a login on the topic for precisely the callers most likely to
 * be touching somebody else's record, which inverts the rule rather than bending it. An audit row that says
 * <em>something changed and we could not name who</em> is honest; one that says <em>somebody changed it</em> under an
 * identifier the consumer cannot join is worse than the gap it papers over.</p>
 */
@Component
public class ActorAccountId {

    private static final Logger LOG = LoggerFactory.getLogger(ActorAccountId.class);

    /**
     * Request-scoped cache key. Holds an {@link Optional} so that "resolved to nobody" is cached too — otherwise the
     * unauthenticated and cross-product cases, which are exactly the ones that resolve to nothing, pay the lookup on
     * every document a request touches.
     */
    private static final String ATTRIBUTE = ActorAccountId.class.getName() + ".accountId";

    private final ProfileRepository profileRepository;

    public ActorAccountId(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * The current caller's {@code accountId}, or empty when this service cannot name them.
     *
     * <p>Never throws. It is called from a persistence callback, where anything thrown would fail the write it is
     * merely describing — the same rule {@code PatientEventPublisher} states for publishing itself.</p>
     *
     * @return the gateway {@code User.id} of the caller, or empty.
     */
    public Optional<String> current() {
        Optional<String> cached = fromRequest();
        if (cached != null) {
            return cached;
        }
        Optional<String> resolved = resolve();
        cache(resolved);
        return resolved;
    }

    private Optional<String> resolve() {
        try {
            return SecurityUtils
                .getCurrentUserEmail()
                .flatMap(profileRepository::findOneByEmailIgnoreCase)
                .map(profile -> profile.getAccountId())
                .filter(accountId -> !accountId.isBlank());
        } catch (RuntimeException e) {
            // Deliberately swallowed, and deliberately without the email: this runs inside a write, and the identifier
            // that would make the line useful is the one item 43 keeps out of logs. The class of the failure is what
            // an operator can act on.
            LOG.warn("Could not resolve the acting account id ({}) — the event will carry no actor", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * @return the cached answer, or {@code null} when nothing has been cached — which is not the same as an empty
     *     {@link Optional}, the cached form of "resolved to nobody".
     */
    private static Optional<String> fromRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Optional<String> cached = (Optional<String>) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return cached;
    }

    private static void cache(Optional<String> accountId) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ATTRIBUTE, accountId, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
