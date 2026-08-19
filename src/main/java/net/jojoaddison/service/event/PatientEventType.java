package net.jojoaddison.service.event;

/**
 * The types carried on {@code patient-events}.
 *
 * <p>Strings rather than an enum because both services publish to this stream and only one of them can own a Java
 * type. A consumer that meets a type it does not know must ignore it, which is also why adding one here is not a
 * breaking change.</p>
 */
public final class PatientEventType {

    /** Emitted by the gateway at registration, and when a care-angel account is created. */
    public static final String ACCOUNT_CREATED = "AccountCreated";

    /** Emitted by the gateway on activation — and at creation for care-angel accounts, which start activated. */
    public static final String ACCOUNT_ACTIVATED = "AccountActivated";

    /** The patient's record now exists. The one event that binds an email to a patientId. */
    public static final String ONBOARDING_STARTED = "OnboardingStarted";

    /** One step answered. Says that it was answered, never what it said. */
    public static final String ONBOARDING_STEP_COMPLETED = "OnboardingStepCompleted";

    public static final String ONBOARDING_COMPLETED = "OnboardingCompleted";

    /** A delegation was accepted, declined, revoked, or ripened from standby. */
    public static final String CARE_DELEGATION_CHANGED = "CareDelegationChanged";

    private PatientEventType() {}
}
