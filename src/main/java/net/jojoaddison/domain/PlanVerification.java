package net.jojoaddison.domain;

import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * That hc-admin's acknowledgement of one plan choice was applied, kept once and read for two purposes.
 *
 * <h2>The idempotency ledger</h2>
 *
 * <p>Delivery on {@code patient-events-plan} is at least once, so a redelivery is normal rather than exceptional and
 * {@code PatientEvent.eventId} exists — its own javadoc says so — for exactly this. <b>The event id is this
 * document's {@code _id}</b>, which makes "have I already applied this frame" a primary-key read and makes a second
 * insert of the same frame impossible rather than merely unlikely. Without it a replay would find the membership no
 * longer {@code PENDING}, refuse, and dead-letter a frame whose only fault was arriving twice.</p>
 *
 * <h2>And the verification record item 19 left unplaced</h2>
 *
 * <p>Item 19 ruled that {@code VERIFIED} is not a {@link net.jojoaddison.domain.enumeration.MembershipStatus} — an
 * administrator's approval sets {@code ACTIVE} directly, and <em>"{@code VERIFIED} is the audit record of who
 * approved and when, never the live status"</em> — and left where that record lives to be settled with this consumer.
 * This is it. A membership carries where it stands; this carries that somebody next door said so, and when.</p>
 *
 * <p><b>It records "when", not "who", and the difference is hc-admin's to close.</b> The payload settled at
 * {@code { Plan }} — one field — so the acknowledgement names no administrator, and inventing one here would be a
 * claim rather than a record. If the identity of the approver is ever wanted, it has to be added to the contract on
 * their side first.</p>
 *
 * <h2>Two things it deliberately is not</h2>
 *
 * <p><b>Not a record of refusals.</b> Only an acknowledgement this service actually applied is written here. A frame
 * that was refused must stay refused on redelivery so that it reaches the dead-letter queue; writing it here would
 * make the first attempt swallow the retries and the DLQ would never fill.</p>
 *
 * <p><b>Not expired.</b> There is no TTL index, so these accumulate — one small document per plan approval in the
 * whole product, which is negligible beside the collections beside it, and the alternative is worse: a ledger entry
 * that expires before Kafka's retention does turns an old replay back into a refusal. If this ever needs bounding,
 * bound it well outside the broker's retention and note that Spring Data does not create indexes automatically here.</p>
 *
 * <p>Nothing on this document is clinical. A plan is a purchase.</p>
 */
@Document(collection = "plan_verification")
public class PlanVerification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The acknowledgement's {@code eventId}. The primary key, which is what makes a replay a key lookup. */
    @Id
    private String id;

    /** The producer's own event type, as sent. Recorded rather than matched on — see {@code PlanVerificationConsumer}. */
    @Field("type")
    private String type;

    /** When hc-admin says the decision happened, or null when their envelope carried no timestamp. */
    @Field("occurred_at")
    private Instant occurredAt;

    /** When this service applied it, which is a fact about our consumption rather than about the decision. */
    @Field("applied_at")
    private Instant appliedAt;

    @Field("patient_id")
    private String patientId;

    @Field("membership_id")
    private String membershipId;

    /** The plan the acknowledgement named, which is the value the consistency check passed on. */
    @Field("plan_code")
    private String planCode;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PlanVerification id(String id) {
        this.setId(id);
        return this;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public PlanVerification type(String type) {
        this.setType(type);
        return this;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public PlanVerification occurredAt(Instant occurredAt) {
        this.setOccurredAt(occurredAt);
        return this;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public PlanVerification appliedAt(Instant appliedAt) {
        this.setAppliedAt(appliedAt);
        return this;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public PlanVerification patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public String getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(String membershipId) {
        this.membershipId = membershipId;
    }

    public PlanVerification membershipId(String membershipId) {
        this.setMembershipId(membershipId);
        return this;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public PlanVerification planCode(String planCode) {
        this.setPlanCode(planCode);
        return this;
    }

    @Override
    public String toString() {
        return (
            "PlanVerification{" +
            "id='" +
            getId() +
            "'" +
            ", type='" +
            getType() +
            "'" +
            ", occurredAt='" +
            getOccurredAt() +
            "'" +
            ", appliedAt='" +
            getAppliedAt() +
            "'" +
            ", patientId='" +
            getPatientId() +
            "'" +
            ", membershipId='" +
            getMembershipId() +
            "'" +
            ", planCode='" +
            getPlanCode() +
            "'" +
            "}"
        );
    }
}
