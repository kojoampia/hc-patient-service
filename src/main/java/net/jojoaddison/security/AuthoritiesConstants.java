package net.jojoaddison.security;

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
     * Clinical staff, who legitimately read across patients. No service issues this authority today;
     * it exists so cross-patient access has a name to be granted to, rather than being the behaviour
     * you get when nobody remembers to write a check.
     */
    public static final String PROFESSIONAL = "ROLE_PROFESSIONAL";

    /**
     * The clinical disciplines, spelled exactly as {@code hc-professional}'s gateway spells them.
     *
     * <h2>Why they appear here without being issued here</h2>
     *
     * <p>This service issues no authorities at all — it validates tokens. {@code hc-patient}'s own gateway mints
     * {@link #PROFESSIONAL} and will go on doing so. These eight are minted by <em>hc-professional</em>'s gateway,
     * and they reach this service because the two stacks share one JWT signing key: a token from either is accepted
     * by either.</p>
     *
     * <p>Which means they were already arriving and being ignored. {@code hc-professional}'s gateway has no
     * {@code ROLE_PROFESSIONAL} at all, so a doctor signing in there reached this service holding
     * {@code ROLE_DOCTOR}, failed every {@code ROLE_PROFESSIONAL} check, resolved to no patient, and was served an
     * empty list rather than a refusal. Naming them is what stops that.</p>
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

    private AuthoritiesConstants() {}
}
