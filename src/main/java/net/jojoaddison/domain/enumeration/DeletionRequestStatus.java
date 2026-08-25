package net.jojoaddison.domain.enumeration;

/**
 * The lifecycle of a {@link net.jojoaddison.domain.DeletionRequest}.
 *
 * <p>{@code PENDING} is the only state in which anything is owed. The other three are terminal, and a patient who
 * wants to be forgotten after a {@code CANCELLED} or {@code REJECTED} request raises a new one rather than reopening
 * the old — same reason {@code DelegationStatus} works that way, so the history of what was asked for, and what was
 * done about it, stays readable after the record it concerned is gone.</p>
 *
 * <p>{@code COMPLETED} is the only state that means data was erased. It is reachable only through
 * {@code DeletionRequestResource.complete}, which is {@code ROLE_ADMIN}-gated, and it is set <em>after</em> the
 * erasure rather than before — a request left {@code PENDING} by a failed erasure is a job still to do, whereas one
 * marked {@code COMPLETED} by an erasure that then failed is a promise recorded as kept and silently broken.</p>
 */
public enum DeletionRequestStatus {
    PENDING,
    CANCELLED,
    COMPLETED,
    REJECTED,
}
