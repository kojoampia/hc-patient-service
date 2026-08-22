package net.jojoaddison.security;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import net.jojoaddison.domain.enumeration.StatSource;
import net.jojoaddison.repository.CareDelegationRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Answers the question every patient-facing endpoint has to ask before it touches data: <em>whose records may this
 * caller see?</em>
 *
 * <p>Until 2026-08-05 nothing asked it. This service authorized on {@code .requestMatchers("/api/**").authenticated()}
 * and nothing else — no method-level rule, no ownership check, no scoping in any query. Every entity already carried a
 * {@code patientId}, and several endpoints already accepted it as a filter, but the filter came from the caller and
 * was never compared with the caller's identity. The practical effect was that any account on the platform could read,
 * modify and delete every patient's profile, clinical cases, conditions, medications, allergies, emergencies, care
 * plan, documents, reports and visits. Registration is open, so obtaining such an account took an email address.</p>
 *
 * <h2>How identity reaches here</h2>
 *
 * <pre>
 *   gateway authenticates -&gt; mints a JWT with an `email` claim
 *     -&gt; this service reads the claim            (SecurityUtils.getCurrentUserEmail)
 *       -&gt; resolves it to a Profile              (ProfileRepository.findOneByEmailIgnoreCase)
 *         -&gt; yields that profile's patientId     (falling back to the profile id)
 *           -&gt; every query is filtered by it
 * </pre>
 *
 * <p>The email hop exists because this service runs with {@code skipUserManagement}: it has no User document and the
 * token's subject is a gateway login that matches nothing here. Email is the only identifier the two systems share,
 * and it is the same chain the dashboard already followed client-side — moved to where it cannot be edited by the
 * person it constrains.</p>
 *
 * <h2>Failing closed</h2>
 *
 * <p>{@link #currentPatientId()} is empty for an unrestricted caller <em>and</em> for a caller who cannot be resolved
 * to a patient, so it must never be read on its own. Call {@link #requireScopeFor} / {@link #isVisible} instead: they
 * distinguish the two, and treat "unknown" as no access. A caller with no Profile sees nothing rather than everything,
 * which is the opposite of how this code behaved before.</p>
 *
 * <h2>Acting for somebody else</h2>
 *
 * <p>A care angel signs in as themselves and <em>acts as</em> the patient, so that decisions can be made when the
 * patient cannot make them. Which patient is named by an {@value #ACTING_AS_HEADER} header:</p>
 *
 * <pre>
 *   no header                      -&gt; the caller's own profile, exactly as before
 *   header naming your own patient -&gt; the same thing, said explicitly
 *   header naming another patient  -&gt; allowed only with an ACTIVE CareDelegation, else denied
 * </pre>
 *
 * <p><strong>The header is not a trust assertion.</strong> It selects among scopes the server confirms
 * independently; a caller who names a patient they hold no active delegation for is refused exactly as if they had
 * guessed a record id.</p>
 *
 * <p>It is a header rather than a claim in the token, and that is the load-bearing choice. A token freezes the
 * delegation at the moment it was minted, so a revoked angel would keep access until it expired — with
 * {@code rememberMe} that is days. Re-reading the delegation per request is what makes revocation take effect on the
 * very next one.</p>
 *
 * <h2>Authorization comes from the delegation, never from the role</h2>
 *
 * <p>{@code ROLE_ANGEL} grants nothing. An {@code ACTIVE} {@link net.jojoaddison.domain.CareDelegation} grants
 * everything, and this class reads that collection — never {@code Profile.careAngelEmail}, which is a display cache
 * that a stale write would turn into a bypass. The role exists so the gateway and the portal can tell that somebody is
 * an angel at all, for menus and the sign-in profile picker. Two things follow that are worth having deliberately: no
 * code has to remember to strip a role when a delegation ends, and a stale token cannot be replayed into access the
 * delegation no longer permits.</p>
 *
 * <p>Until care delegation existed this class said, correctly, that {@code ROLE_ANGEL} was scoped exactly like a
 * patient because no delegation was recorded anywhere in the platform. One is recorded now.</p>
 *
 * <p>{@code ROLE_PROFESSIONAL} and {@code ROLE_ADMIN} remain unrestricted, which is the point of naming them —
 * cross-patient access is something a role grants, not something you get when nobody remembers to write a check. They
 * ignore the header: it exists to narrow a caller to one patient, and they are not narrowed to begin with.</p>
 *
 * <h2>What is deliberately not solved here</h2>
 *
 * <p>An angel who is also a patient in their own right must choose, per request, which of the two they are acting as;
 * this class only enforces that the choice was one they were entitled to make. Nothing here decides <em>who may
 * delete</em> either — since patient data became undeletable that is a flat {@code ROLE_ADMIN} check on the endpoints
 * themselves, not a question about scope.</p>
 */
@Component
public class PatientScope {

    private static final Logger LOG = LoggerFactory.getLogger(PatientScope.class);

    /** Names the patient the caller wants to act as. Absent means "myself". */
    public static final String ACTING_AS_HEADER = "X-Acting-As";

    /**
     * Where the resolved scope is parked for the rest of the request.
     *
     * <p>Resolving costs up to two Mongo queries and every scoped operation asks, several times per request. Caching
     * on the request rather than anywhere longer-lived is deliberate: a delegation revoked between two requests must
     * take effect on the second, which is the whole reason the selection travels as a header rather than in the
     * token.</p>
     */
    private static final String SCOPE_ATTRIBUTE = PatientScope.class.getName() + ".scope";

    private final ProfileRepository profileRepository;
    private final CareDelegationRepository careDelegationRepository;

    public PatientScope(ProfileRepository profileRepository, CareDelegationRepository careDelegationRepository) {
        this.profileRepository = profileRepository;
        this.careDelegationRepository = careDelegationRepository;
    }

    /**
     * Who the caller is acting for, and whether they had to be a delegate to do it.
     *
     * @param patientId the patient in scope, or null for an unrestricted caller and for one who cannot be resolved.
     * @param actingAsAngel true when the scope came from a delegation rather than from the caller's own profile.
     */
    public record Scope(String patientId, boolean actingAsAngel) {
        private static final Scope NONE = new Scope(null, false);
    }

    /**
     * Whether the caller may read across patients.
     *
     * @return true for administrators and clinical staff.
     */
    public boolean isUnrestricted() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.PROFESSIONAL);
    }

    /**
     * The patient the caller is confined to.
     *
     * <p>Empty means either "unrestricted" or "could not be resolved" — the two are not distinguishable from this
     * method alone and must not be treated as the same thing. Prefer {@link #requireScopeFor} or {@link #isVisible}.</p>
     *
     * @return the caller's patientId, or empty.
     */
    public Optional<String> currentPatientId() {
        return Optional.ofNullable(resolve().patientId());
    }

    /**
     * Whether the caller reached this patient through a delegation rather than by being them.
     *
     * <p>What it gates is small and specific: an angel may read and write the record, but may not re-run onboarding on
     * the patient's behalf, and may not edit the fields that decide who the angel is
     * ({@code email}, {@code careAngelEmail}, {@code careAngelLogin}) — otherwise an angel could hand their access to
     * a third party, or lock the patient's own nominee out.</p>
     *
     * @return true when acting as somebody else's delegate.
     */
    public boolean isActingAsAngel() {
        return resolve().actingAsAngel();
    }

    /**
     * Who is writing this record, for the {@code source} field on a clinical document.
     *
     * <p>Derived from the caller and <strong>never taken from the payload</strong>. A value a client can choose is a
     * claim rather than a record: without this, anyone could post an allergy marked {@code PROFESSIONAL} and have it
     * read, forever afterwards, as clinician-attested.</p>
     *
     * <p>Only on create. An update preserves whatever the stored document already says, because provenance is about
     * where a record came from — a clinician correcting a typo in a patient's self-report does not turn it into a
     * clinical finding.</p>
     *
     * @return {@code PROFESSIONAL} for clinical staff and administrators, {@code ANGEL} for a delegate acting on a
     *         patient's behalf, {@code PATIENT} otherwise.
     */
    public ActivitySource currentActivitySource() {
        if (isUnrestricted()) {
            return ActivitySource.PROFESSIONAL;
        }
        return isActingAsAngel() ? ActivitySource.ANGEL : ActivitySource.PATIENT;
    }

    /**
     * The {@link #currentActivitySource()} of a reading.
     *
     * <p>A separate enum because a {@code Stat} can come from a {@code DEVICE} — a cuff or a meter reporting on its
     * own — which is not something that can write a timeline entry. Nothing here ever returns {@code DEVICE}: a device
     * does not hold a token, and when telemetry ingestion exists it will arrive by a different path.</p>
     */
    public StatSource currentStatSource() {
        if (isUnrestricted()) {
            return StatSource.PROFESSIONAL;
        }
        return isActingAsAngel() ? StatSource.ANGEL : StatSource.PATIENT;
    }

    /**
     * The email of a caller who is creating their very first {@code Profile}.
     *
     * <p>Unlike {@link #requirePatientIdForWrite} this does <em>not</em> require an existing profile — it is the one
     * path that may run before one exists, and so the one place the usual "no profile means no access" rule cannot
     * apply. It is safe only because it derives identity solely from the token, and because its caller refuses to run
     * when a profile for that email is already there. Both halves are needed; this method alone authorizes nothing.</p>
     *
     * @return the token's email claim.
     * @throws AccessDeniedException if the token carries no usable email.
     */
    public String bootstrapEmail() {
        return SecurityUtils
            .getCurrentUserEmail()
            .orElseThrow(() ->
                // The gateway issues an account with no email an unscoped token, and this service already reads that
                // as "no records at all". It has to mean "cannot onboard" too — the alternative is that the one
                // identity we cannot pin down is the one that gets to create a patient.
                new AccessDeniedException("This account has no email address, so it cannot be resolved to a patient")
            );
    }

    /**
     * Works out whose records this caller may touch, honouring {@value #ACTING_AS_HEADER}.
     *
     * @return the resolved scope; never null.
     * @throws AccessDeniedException if the caller names a patient they hold no active delegation for.
     */
    private Scope resolve() {
        if (isUnrestricted()) {
            // An unrestricted caller who has NAMED a patient is narrowed to them. The role still decides what may be
            // done; it stops deciding who is being looked at.
            //
            // Before this, the header was not read at all on this path, so an administrator who picked a patient in
            // the portal was served every patient's records while the acting-as banner named one person. A screen
            // showing one name over another patient's blood group is the exact failure the banner exists to prevent,
            // and it answered 200 throughout.
            //
            // Note which direction this runs: an administrator already reads every record, so honouring the header
            // takes access away rather than granting it. Nothing here lets a caller reach a patient they could not
            // already reach — a restricted caller still falls through to the delegation check below.
            //
            // actingAsAngel stays false: they are not a delegate. It gates the small set of things an angel may not
            // do — re-running onboarding, editing the fields that decide who the angel is — and an administrator's
            // authority over those does not come from a delegation, so it should not be revoked by one.
            String requested = actingAsHeader();
            return requested == null ? Scope.NONE : new Scope(requested, false);
        }
        Optional<Scope> cached = cachedScope();
        if (cached.isPresent()) {
            return cached.orElseThrow();
        }
        Scope scope = resolveUncached();
        cacheScope(scope);
        return scope;
    }

    private Scope resolveUncached() {
        Optional<String> email = SecurityUtils.getCurrentUserEmail();
        if (email.isEmpty()) {
            return Scope.NONE;
        }
        String own = profileRepository
            .findOneByEmailIgnoreCase(email.orElseThrow())
            // patientId is the identifier the collections are keyed by, but profiles written before the field existed
            // only have their own id — the same fallback the dashboard uses, so both agree on who a person is.
            .map(profile -> Optional.ofNullable(profile.getPatientId()).orElse(profile.getId()))
            .orElse(null);

        String requested = actingAsHeader();
        if (requested == null || requested.equals(own)) {
            // No header, or one that names the caller themselves. The second is the common case once the portal has
            // made a choice — it always sends the header thereafter — so it must not be treated as an exception.
            return new Scope(own, false);
        }

        // Naming somebody else. This is the only path that consults a delegation, and it consults the delegation
        // itself rather than Profile.careAngelEmail: the cache does not know whether the delegation is pending,
        // dormant or revoked, and reading it here would keep granting access after a revocation.
        return careDelegationRepository
            .findOneByAngelEmailIgnoreCaseAndStatus(email.orElseThrow(), DelegationStatus.ACTIVE)
            .filter(delegation -> requested.equals(delegation.getPatientId()))
            .map(delegation -> new Scope(delegation.getPatientId(), true))
            .orElseThrow(() -> {
                LOG.warn("Rejected an acting-as request: the caller holds no active delegation for the patient named");
                return new AccessDeniedException("No active care delegation lets this account act for that patient");
            });
    }

    private static String actingAsHeader() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        String value = servletAttributes.getRequest().getHeader(ACTING_AS_HEADER);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Optional<Scope> cachedScope() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null
            ? Optional.empty()
            : Optional.ofNullable((Scope) attributes.getAttribute(SCOPE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    private static void cacheScope(Scope scope) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(SCOPE_ATTRIBUTE, scope, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /**
     * Runs a collection query under the caller's scope, choosing between the unfiltered and the by-patient query.
     *
     * <p>This is the only form a resource should use for a list endpoint, and it is shaped as "hand me both queries
     * and I will pick" rather than "here is the patientId to use" deliberately: there is no way to call it and forget
     * to apply the result, and no sentinel value a caller could mistake for a real id.</p>
     *
     * <p>For a restricted caller the requested filter is ignored unless it matches their own — asking for someone
     * else's records yields an empty list rather than an error, indistinguishable from a patient who simply has no
     * data, so the endpoint leaks nothing about whether the other patient exists. For an unrestricted caller the
     * requested value is honoured, and null means every patient.</p>
     *
     * @param requestedPatientId the {@code patientId} query parameter, or null.
     * @param findAll the unfiltered query, used only for unrestricted callers.
     * @param findByPatientId the scoped query.
     * @param <T> the entity type.
     * @return the records the caller is allowed to see.
     */
    public <T> List<T> findScoped(String requestedPatientId, Supplier<List<T>> findAll, Function<String, List<T>> findByPatientId) {
        // The scope is consulted before the role, so that an unrestricted caller who has chosen a patient is confined
        // to them like anybody else. Only a caller with no scope at all falls through to the role, and for them
        // "unrestricted" still means every patient.
        Optional<String> scope = currentPatientId();
        if (scope.isEmpty()) {
            if (isUnrestricted()) {
                return requestedPatientId == null ? findAll.get() : findByPatientId.apply(requestedPatientId);
            }
            LOG.debug("Returning no records: the caller has no resolvable profile");
            return List.of();
        }
        if (requestedPatientId != null && !requestedPatientId.equals(scope.orElseThrow())) {
            LOG.warn("Rejected a cross-patient query: caller asked for a patientId outside the record they have open");
            return List.of();
        }
        return findByPatientId.apply(scope.orElseThrow());
    }

    /**
     * The paged form of {@link #findScoped}, for the endpoints that return a {@code Page}.
     *
     * <p>A separate name rather than an overload: {@code MongoRepository} declares {@code findAll()},
     * {@code findAll(Sort)} and {@code findAll(Pageable)}, and a method reference to it against two overloads
     * differing only in the functional interface's type argument is exactly the shape that makes inference pick the
     * wrong one — silently, and here that would mean serving an unscoped query.</p>
     *
     * @param requestedPatientId the {@code patientId} query parameter, or null.
     * @param pageable the page being asked for, used to shape the empty page on denial.
     * @param findAll the unfiltered query, used only for unrestricted callers.
     * @param findByPatientId the scoped query.
     * @param <T> the entity type.
     * @return the page the caller is allowed to see.
     */
    public <T> Page<T> findScopedPage(
        String requestedPatientId,
        Pageable pageable,
        Function<Pageable, Page<T>> findAll,
        BiFunction<String, Pageable, Page<T>> findByPatientId
    ) {
        // Same order as findScoped, and for the same reason: scope first, role only for a caller who has none.
        Optional<String> scope = currentPatientId();
        if (scope.isEmpty()) {
            if (isUnrestricted()) {
                return requestedPatientId == null ? findAll.apply(pageable) : findByPatientId.apply(requestedPatientId, pageable);
            }
            LOG.debug("Returning no records: the caller has no resolvable profile");
            return Page.empty(pageable);
        }
        if (requestedPatientId != null && !requestedPatientId.equals(scope.orElseThrow())) {
            LOG.warn("Rejected a cross-patient query: caller asked for a patientId outside the record they have open");
            return Page.empty(pageable);
        }
        return findByPatientId.apply(scope.orElseThrow(), pageable);
    }

    /**
     * The patientId a record must keep when it is updated.
     *
     * <p>For a patient this is always the stored value: they may edit their own records but never move one to somebody
     * else, and never move somebody else's to themselves. For an administrator or clinician it is whatever the payload
     * says, because correcting a misfiled record is a legitimate operation and the only people who can do it are the
     * ones the role names.</p>
     *
     * @param storedPatientId the owner currently recorded on the stored document.
     * @param requestedPatientId the owner in the request body.
     * @return the value to persist.
     */
    public String patientIdForUpdate(String storedPatientId, String requestedPatientId) {
        // Refiling is a cross-patient operation, so it belongs to a caller who is looking across patients. An
        // administrator who has chosen one record is not, and while the choice stands they move records no more than
        // an angel does — otherwise the way to move a record out of the open patient would be to open that patient.
        if (isUnrestricted() && currentPatientId().isEmpty()) {
            return requestedPatientId == null ? storedPatientId : requestedPatientId;
        }
        return storedPatientId;
    }

    /**
     * The patientId to stamp on a record being created, or a refusal.
     *
     * @param requestedPatientId the value in the request body.
     * @return the value to persist.
     * @throws AccessDeniedException if the caller cannot be resolved to a patient and is not unrestricted.
     */
    public String requirePatientIdForWrite(String requestedPatientId) {
        // A caller with a scope writes into it, whoever they are. For an administrator acting as a patient that is
        // the record they have open: creating something while one patient is on screen and having it land on another
        // is the write-side of the same confusion the banner exists to prevent.
        Optional<String> scope = currentPatientId();
        if (scope.isPresent()) {
            return scope.orElseThrow();
        }
        if (isUnrestricted()) {
            // Whatever the payload says, INCLUDING NOTHING. An administrator or clinician creating a record with no
            // owner is what these entities did before they were scoped, and refusing it breaks the reference-shaped
            // ones — an Address filed against no particular patient — for no security gain, since an unrestricted
            // caller could set any owner it liked anyway.
            //
            // Going through Optional here is what made this wrong at first: Optional.ofNullable(null) is empty, and
            // "the admin supplied no owner" then became indistinguishable from "this caller may not write at all".
            return requestedPatientId;
        }
        // Restricted, and no scope resolved: there is no profile behind this account to own the record.
        throw new AccessDeniedException("No patient profile is associated with this account, so it cannot own records");
    }

    /**
     * Whether a record belonging to {@code patientId} may be seen by the caller.
     *
     * @param patientId the record's owning patient, possibly null on legacy documents.
     * @return true if the caller may see it.
     */
    public boolean isVisible(String patientId) {
        Optional<String> scope = currentPatientId();
        if (scope.isEmpty()) {
            return isUnrestricted();
        }
        // A record with no owner is visible to no patient. Such documents exist (the field was added after some data
        // was written); making them universally readable would be a hole exactly the shape of the one being closed.
        //
        // This is what stops the single-record reads leaking past the chosen patient: without it an administrator
        // acting as one patient could still GET another patient's record by id, and the list endpoints would be the
        // only thing the choice narrowed.
        return patientId != null && scope.filter(patientId::equals).isPresent();
    }
}
