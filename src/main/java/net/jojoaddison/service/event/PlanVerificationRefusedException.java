package net.jojoaddison.service.event;

/**
 * An acknowledgement on {@code patient-events-plan} that this service will not apply.
 *
 * <h2>Thrown rather than logged, because throwing is what keeps the frame</h2>
 *
 * <p>Every refusal here is a decision an administrator took next door that has <em>not</em> taken effect. Swallowing
 * one would leave a log line, and nothing in this stack alerts on log lines — {@code deploy/observability/alert-rules.yml}
 * says so in as many words. Throwing sends the frame, bytes intact, to {@code patient-events-plan.hc-patient-dlq},
 * where it can be read to see what was actually sent and replayed once whatever caused the refusal is fixed. <b>A
 * refusal is recoverable; a log line is not.</b></p>
 *
 * <p><b>It does not stall the partition and it does not kill the binding.</b> The binder retries {@code maxAttempts}
 * times, dead-letters, commits the offset and takes the next frame — which is the whole reason the DLQ went in with
 * the binding rather than after it. A refusal costs one patient's verification, never the subscription.</p>
 *
 * <h2>Why the reason is an enum and not just a sentence</h2>
 *
 * <p>Because the reasons are not interchangeable and the tests must be able to say which one fired. "Refused" is one
 * assertion; "refused because two memberships were pending" and "refused because the plan disagreed" are the two the
 * consumer could most easily confuse, and a test matching on message text would pass with either. The message carries
 * the detail — which email, which plan, how many — and the reason carries the category.</p>
 */
public class PlanVerificationRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Why an acknowledgement was not applied.
     *
     * <p>Ordered as the consumer checks them: the shape of the frame first, then the patient, then the membership.
     * Cheap checks before database reads, and a producer defect reported as a producer defect whether or not the
     * patient it names happens to exist.</p>
     */
    public enum Reason {
        /** No {@code eventId}, so the frame cannot be deduplicated and a replay could not be told from a retry. */
        NO_EVENT_ID,

        /**
         * No {@code subject.email}. Every frame in this estate is keyed on the lower-cased email, and one without it
         * names nobody — the same shape {@link PatientEventPublisher} refuses to <em>send</em>.
         */
        NO_SUBJECT_KEY,

        /** The payload named no plan, so the consistency check the payload exists for cannot be made. */
        NO_PLAN_NAMED,

        /**
         * The frame named a status this service has no constant for.
         *
         * <p>This is the live hazard, not a hypothetical: hc-admin's item 54 still records our vocabulary as six
         * values including {@code VERIFIED}, and this repo shipped five. If their console is built from their own
         * entry it will offer an administrator a decision that means nothing here. Refusing names the value and the
         * five it could have been, in this repository's log, which is the cheapest place in the estate to find it.
         */
        STATUS_NOT_IN_THIS_SERVICES_VOCABULARY,

        /**
         * The frame named a status this service recognises but this exchange does not carry.
         *
         * <p>The write-back leg moves a membership from {@code PENDING} to {@code ACTIVE} and does nothing else.
         * Applying an arbitrary status because a sibling asked would be a far larger grant than the contract, and it
         * would route around the write guard that restricts exactly this transition over HTTP.
         */
        STATUS_NOT_AN_ACTIVATION,

        /** No profile with that email. Either the patient does not exist here or hc-admin is keyed on something else. */
        UNKNOWN_PATIENT,

        /** The patient holds no {@code PENDING} membership, so there is nothing this acknowledgement can be about. */
        NO_PENDING_MEMBERSHIP,

        /**
         * The patient holds more than one {@code PENDING} membership.
         *
         * <p>Item 19 decided this refuses rather than guesses: silently picking one of two pending memberships
         * activates a year of care nobody sold. Refusal is loud and recoverable; a wrong pick is neither.
         */
        MORE_THAN_ONE_PENDING_MEMBERSHIP,

        /**
         * The plan named in the acknowledgement is not the plan the membership holds.
         *
         * <p>The reason {@code plan} is in a one-field payload at all. It is a value this service wrote itself, so it
         * carries no new information — its use is precisely this refusal, which turns a silent mismatch into a stop.
         * <b>Do not delete the check as redundant.</b>
         */
        PLAN_DISAGREES,

        /**
         * The membership stopped being {@code PENDING} between the count and the write.
         *
         * <p>What the conditional update in {@code MembershipService.activateIfPending} answers instead of
         * overwriting. Somebody else's decision is a real decision and this one is stale.
         */
        RACED_BY_ANOTHER_WRITER,
    }

    private final Reason reason;

    public PlanVerificationRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
