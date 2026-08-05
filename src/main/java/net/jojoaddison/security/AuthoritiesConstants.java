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

    private AuthoritiesConstants() {}
}
