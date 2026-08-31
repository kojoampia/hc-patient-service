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

    /**
     * A deletion request was raised, withdrawn, carried out or refused.
     *
     * <p>One type with a {@code change} discriminator rather than four, for the reason
     * {@link #CARE_DELEGATION_CHANGED} has one: the consumer is a single mail dispatcher either way, and one type
     * on one topic keeps ordering per patient — a {@code COMPLETED} that overtook its own {@code RAISED} would
     * mail somebody that their record is gone before telling them it was going.</p>
     *
     * <p><b>{@code COMPLETED} is the one event on this stream whose subject no longer exists.</b> The erasure takes
     * the {@code Profile} with it, so the email cannot be looked up when the event is built and is read off the
     * stored request instead. A consumer must not try to resolve the patient.</p>
     */
    public static final String DELETION_REQUEST_CHANGED = "DeletionRequestChanged";

    private PatientEventType() {}
}
