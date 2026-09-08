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

    /**
     * A patient chose a care plan. The {@code Membership} is written, and for anybody but an administrator it is
     * written {@code PENDING} — so this says a subscription was <em>requested</em>, not that one is in force.
     *
     * <p><strong>hc-admin is contracted to consume this and does not yet</strong> — their item 48 is open, and their
     * {@code SiblingEventParser} has no {@code PlanChosen} case today, so the frame currently falls to its
     * {@code default} and is logged at DEBUG as a type that service does not model. Nothing is lost by publishing
     * before they are ready, and the contract is worth pinning first; but do not read this as a closed loop. Their
     * parser dispatches on the type string, so <strong>{@code "PlanChosen"} is a cross-repo contract and must not be
     * renamed casually</strong>:
     * a rename here is not a compile error there, it is an event their {@code switch} silently ignores. Renaming it
     * means changing both repositories and both sides' tests in the same breath.</p>
     *
     * <p>Payload: {@code membershipId}, {@code planCode}, {@code planName}, {@code status}. <b>{@code planCode} is
     * {@code Membership.plan} and {@code planName} is {@code Membership.name}</b> — the document has no {@code code}
     * field, and both clients' {@code choosePlan} write {@code plan.code} into {@code plan} and {@code plan.name}
     * into {@code name}. The patient themselves travels in {@code subject}, as on every other event here: the
     * lowercased email is the key, and {@code patientId} rides beside it.</p>
     *
     * <p>A plan is commercial rather than clinical — a code, a name and a status — so it passes
     * {@link PatientEventPublisher#assertNothingClinical}. The membership's {@code description} is deliberately not
     * carried: it is free text a client supplies, and this event says which plan was chosen, not what was said
     * about it.</p>
     *
     * <p><strong>What the membership does not have yet, and what that implies for the consumer.</strong> A membership
     * created through either client carries <em>no</em> {@code memberNumber} and <em>no</em> {@code renewalDate} —
     * {@code choosePlan} sets neither, and nothing in this service assigns them afterwards (backlog item 17). So on
     * the path this event exists for, their absence is a fact rather than an omission: if assigning them is the
     * back-office step this event exists to prompt, that work has no home in this service today and there is no
     * inbound path for it either. The acknowledgement leg, {@code patient-events-plan}, is backlog item 19 and is not
     * built.</p>
     *
     * <p><b>The administrative CRUD path is the exception</b>, and a consumer should not generalise from the sentence
     * above: {@code POST /api/memberships} accepts a {@code memberNumber} and a {@code renewalDate} from an
     * administrator and persists both, so a membership created that way can carry values this event does not
     * publish. The payload is fixed at four fields for the clients' sake; read the document if you need the rest.</p>
     */
    public static final String PLAN_CHOSEN = "PlanChosen";

    private PatientEventType() {}
}
