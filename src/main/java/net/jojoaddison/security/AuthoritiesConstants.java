package net.jojoaddison.security;

import java.util.Set;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    /**
     * A patient: may only ever reach their own records. Enforced by scoping every query to the
     * {@code patientId} claim in the token — see {@link SecurityUtils#getCurrentPatientId()}.
     */
    public static final String PATIENT = "ROLE_PATIENT";

    /**
     * A patient's nominated carer ("angel"). Modelled here so the authority can be carried and
     * checked, but it grants nothing yet: which patients an angel may see is a delegation the
     * platform does not record anywhere, so an angel is currently scoped exactly like a patient —
     * to their own records. Widening that needs a delegation model, not a change here.
     */
    public static final String ANGEL = "ROLE_ANGEL";

    /**
     * The clinical disciplines, spelled exactly as {@code hc-professional}'s gateway spells them.
     *
     * <h2>They are the only clinical authorities left</h2>
     *
     * <p>{@code ROLE_PROFESSIONAL} used to sit here — a single blanket authority meaning "clinical staff" — and it
     * was removed on 2026-08-24 because nothing in the platform issued it except {@code hc-patient}'s own gateway,
     * and nothing outside this service ever checked it. It was an authority this subsystem minted for itself and
     * then required of everybody else. A clinician signing in to {@code hc-professional} — the portal that owns the
     * case queue — arrived holding {@code ROLE_DOCTOR}, failed every {@code ROLE_PROFESSIONAL} check, resolved to no
     * patient, and was served an empty list rather than a refusal.</p>
     *
     * <p>These eight are minted by <em>hc-professional</em>'s gateway and reach this service because the two stacks
     * share one JWT signing key: a token from either is accepted by either. Since 2026-08-24 {@code hc-patient}'s
     * gateway seeds the same eight, so both halves of the platform now name a clinician the same way.</p>
     *
     * <p>The strings must stay byte-identical to {@code hc-professional}'s {@code AuthoritiesConstants}. There is no
     * shared artefact between the two repositories to enforce it, which is precisely why it is written down here.</p>
     */
    public static final String DOCTOR = "ROLE_DOCTOR";

    public static final String NURSE = "ROLE_NURSE";

    public static final String CARER = "ROLE_CARER";

    public static final String PARAMEDIC = "ROLE_PARAMEDIC";

    public static final String PHARMACIST = "ROLE_PHARMACIST";

    public static final String THERAPIST = "ROLE_THERAPIST";

    public static final String CHEMIST = "ROLE_CHEMIST";

    public static final String TECHNICIAN = "ROLE_TECHNICIAN";

    /**
     * Every clinical discipline, for the checks that mean "any clinician" rather than a particular one.
     *
     * <p>This is the replacement for {@code ROLE_PROFESSIONAL} on the reference-data endpoints — duty rosters,
     * shifts, teams, the professional directory, metadata and recommendations — where the question really is
     * whether the caller is clinical staff at all, and no discipline has a better claim than another to read a
     * roster. It is <em>not</em> the replacement on the endpoints that turn on a clinical judgement; those name the
     * discipline that may make it, and {@link ScopeOfPractice} is the table that decides which.</p>
     *
     * <p>Use this in Java. For {@code @PreAuthorize}, use {@link #CLINICAL_AUTHORITIES}, which is the same eight.</p>
     */
    public static final Set<String> CLINICAL = Set.of(DOCTOR, NURSE, CARER, PARAMEDIC, PHARMACIST, THERAPIST, CHEMIST, TECHNICIAN);

    /**
     * {@link #CLINICAL}, pre-quoted for a SpEL {@code hasAnyAuthority(...)} argument list.
     *
     * <p>Two constants for one set is a drift risk, and it is taken deliberately: {@code @PreAuthorize} takes a
     * compile-time constant string and cannot read a {@code Set}. Concatenating the constants rather than retyping
     * the literals means the two cannot disagree about how a role is <em>spelled</em>; they can still disagree about
     * <em>membership</em> when a ninth discipline is added to one and not the other, so
     * {@code AuthoritiesConstantsUnitTest} asserts they name the same eight. That test is the only thing standing
     * between a new discipline and a silent hole in twenty-four checks.</p>
     *
     * <p>Written as {@code hasAnyAuthority(" + CLINICAL_AUTHORITIES + ")} — the quotes are inside this constant, so
     * do not add your own.</p>
     */
    public static final String CLINICAL_AUTHORITIES =
        "'" +
        DOCTOR +
        "', '" +
        NURSE +
        "', '" +
        CARER +
        "', '" +
        PARAMEDIC +
        "', '" +
        PHARMACIST +
        "', '" +
        THERAPIST +
        "', '" +
        CHEMIST +
        "', '" +
        TECHNICIAN +
        "'";

    private AuthoritiesConstants() {}
}
