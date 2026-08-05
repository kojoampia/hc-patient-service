package net.jojoaddison.security;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.jojoaddison.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

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
 * <h2>What is deliberately not solved here</h2>
 *
 * <p>{@code ROLE_ANGEL} is scoped exactly like a patient. An angel is a carer nominated by a patient, but no
 * delegation is recorded anywhere in the platform, so there is nothing to authorize against; inventing one here would
 * be inventing a security model. {@code ROLE_PROFESSIONAL} and {@code ROLE_ADMIN} are unrestricted, which is the point
 * of naming them — cross-patient access is now something a role grants, not something you get when nobody remembers to
 * write a check.</p>
 */
@Component
public class PatientScope {

    private static final Logger LOG = LoggerFactory.getLogger(PatientScope.class);

    private final ProfileRepository profileRepository;

    public PatientScope(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
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
        if (isUnrestricted()) {
            return Optional.empty();
        }
        return SecurityUtils
            .getCurrentUserEmail()
            .flatMap(profileRepository::findOneByEmailIgnoreCase)
            // patientId is the identifier the collections are keyed by, but profiles written before the field existed
            // only have their own id — the same fallback the dashboard uses, so both agree on who a person is.
            .map(profile -> Optional.ofNullable(profile.getPatientId()).orElse(profile.getId()));
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
        if (isUnrestricted()) {
            return requestedPatientId == null ? findAll.get() : findByPatientId.apply(requestedPatientId);
        }
        Optional<String> own = currentPatientId();
        if (own.isEmpty()) {
            LOG.debug("Returning no records: the caller has no resolvable profile");
            return List.of();
        }
        if (requestedPatientId != null && !requestedPatientId.equals(own.orElseThrow())) {
            LOG.warn("Rejected a cross-patient query: caller asked for a patientId that is not their own");
            return List.of();
        }
        return findByPatientId.apply(own.orElseThrow());
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
        if (isUnrestricted()) {
            return requestedPatientId == null ? findAll.apply(pageable) : findByPatientId.apply(requestedPatientId, pageable);
        }
        Optional<String> own = currentPatientId();
        if (own.isEmpty()) {
            LOG.debug("Returning no records: the caller has no resolvable profile");
            return Page.empty(pageable);
        }
        if (requestedPatientId != null && !requestedPatientId.equals(own.orElseThrow())) {
            LOG.warn("Rejected a cross-patient query: caller asked for a patientId that is not their own");
            return Page.empty(pageable);
        }
        return findByPatientId.apply(own.orElseThrow(), pageable);
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
        if (isUnrestricted()) {
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
        return currentPatientId()
            .orElseThrow(() -> new AccessDeniedException("No patient profile is associated with this account, so it cannot own records"));
    }

    /**
     * Whether a record belonging to {@code patientId} may be seen by the caller.
     *
     * @param patientId the record's owning patient, possibly null on legacy documents.
     * @return true if the caller may see it.
     */
    public boolean isVisible(String patientId) {
        if (isUnrestricted()) {
            return true;
        }
        // A record with no owner is visible to no patient. Such documents exist (the field was added after some data
        // was written); making them universally readable would be a hole exactly the shape of the one being closed.
        return patientId != null && currentPatientId().filter(patientId::equals).isPresent();
    }
}
