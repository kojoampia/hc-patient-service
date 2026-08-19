package net.jojoaddison.domain.enumeration;

/**
 * The lifecycle of a {@link net.jojoaddison.domain.CareDelegation}.
 *
 * <p>Only {@code ACTIVE} grants anything. Everything else — including {@code STANDBY}, which looks like a nomination
 * and is not one — resolves to no access at all.</p>
 *
 * <p>{@code STANDBY} is a dormant nomination the patient consented to in advance, for the case the whole arrangement
 * exists to serve: a patient who is incapacitated and never got round to nominating anyone. It cannot reach
 * {@code ACTIVE} directly. Three gates stand between it and access — the patient's advance consent, recorded when they
 * were able to give it; two <em>different</em> professionals, one declaring the incapacity and one countersigning; and
 * finally the nominee's own acceptance, because taking on someone else's medical decisions is not something a person
 * can be conscripted into.</p>
 *
 * <p>{@code DECLINED} and {@code REVOKED} are terminal. Re-nominating the same person creates a new delegation rather
 * than reopening an old one, so the history of who could act, and when, stays readable.</p>
 */
public enum DelegationStatus {
    STANDBY,
    AWAITING_COUNTERSIGNATURE,
    PENDING,
    ACTIVE,
    DECLINED,
    REVOKED,
}
