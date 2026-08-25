package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import net.jojoaddison.domain.enumeration.DeletionRequestStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A patient's standing request to have their record erased, and the record of what was done about it.
 *
 * <h2>Asking and doing are different acts, by different people</h2>
 *
 * <p>A patient raises this. Only {@code ROLE_ADMIN} acts on it. That split is the whole design and it is not
 * ceremony: erasure runs across sixteen collections and a GridFS bucket, it is irreversible, and the thing that
 * triggers it is a tap on a phone. Everything else in this service treats patient data as undeletable — see
 * {@link net.jojoaddison.security.PatientScope} — and this document is the exception that proves it, because it is
 * how the deletion gets asked for without the asking being the doing.</p>
 *
 * <h2>Fourteen days, and why the date is stored</h2>
 *
 * <p>{@link #dueAt} is written once, at {@link #requestedAt} plus fourteen days, and never recomputed. The published
 * privacy policy promises the patient a date; a date derived at read time from a constant would move if the constant
 * ever did, silently rewriting a promise already made to somebody. Change the window and every request raised before
 * the change keeps the deadline it was given.</p>
 *
 * <p>The window is also a cooling-off period. A patient may cancel their own {@code PENDING} request, which is what
 * makes a mis-tap survivable and is the reason the deletion is not immediate.</p>
 *
 * <h2>This document outlives the record it names</h2>
 *
 * <p>It is deliberately <em>not</em> erased by the erasure it commissions. Deleting it along with everything else
 * would leave no evidence that the deletion was asked for, authorised, or carried out, which is exactly the evidence
 * a regulator asks for. It keeps a patient id, the email that raised it, and counts — never clinical content — so
 * what survives is an audit trail rather than a residue of the record.</p>
 */
@Schema(description = "A patient's request to have their record erased, and the record of what was done about it.")
@Document(collection = "deletionrequest")
@CompoundIndexes(
    {
        // "Does this patient already have one open?" — asked on every raise, and on every sign-in by the clients that
        // show the pending banner.
        @CompoundIndex(name = "dr_patient_status", def = "{'patient_id': 1, 'status': 1}"),
        // The administrator's queue: pending work, oldest deadline first.
        @CompoundIndex(name = "dr_status_due", def = "{'status': 1, 'due_at': 1}"),
    }
)
public class DeletionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /** The record to be erased. Kept after erasure: an audit trail naming nobody proves nothing. */
    @Field("patient_id")
    private String patientId;

    /**
     * The account that raised it, from the token's email claim rather than from the payload.
     *
     * <p>A delegate cannot raise one at all (the resource refuses while acting as somebody else), so this is always
     * the patient's own address — but it is recorded rather than assumed, because the assumption is the kind that
     * stops being true when somebody adds a second way in.</p>
     */
    @Field("requested_by_email")
    private String requestedByEmail;

    @Field("requested_by_login")
    private String requestedByLogin;

    @Field("status")
    private DeletionRequestStatus status;

    /**
     * The patient's own words, optional. Never required — nobody has to justify wanting to leave.
     *
     * <p>Deliberately absent from {@link #toString()}. Everything else on this document is a date, a status or an
     * identifier; this is the one free-text field a patient writes, it is written at a moment of some distress, and
     * {@code toString()} is what ends up in a log line.</p>
     */
    @Field("reason")
    private String reason;

    @Field("requested_at")
    private Instant requestedAt;

    /** {@link #requestedAt} + the published window. Written once; see the class comment. */
    @Field("due_at")
    private Instant dueAt;

    @Field("cancelled_at")
    private Instant cancelledAt;

    @Field("completed_at")
    private Instant completedAt;

    /**
     * The administrator who carried out the erasure.
     *
     * <p>A login rather than an id, for the same reason {@code Report.archivedById} is one: this service has no
     * {@code User} document and no reliable mapping from a gateway login to anything local.</p>
     */
    @Field("completed_by_login")
    private String completedByLogin;

    @Field("rejected_at")
    private Instant rejectedAt;

    @Field("rejected_by_login")
    private String rejectedByLogin;

    /**
     * Why an administrator refused, required when they do.
     *
     * <p>Refusing is legitimate — a legal hold, an open investigation, a request that is plainly somebody else's
     * account — and each of those is a reason a patient is owed. It is required precisely so that "no" is never
     * recorded bare.</p>
     */
    @Field("decision_reason")
    private String decisionReason;

    /**
     * What the erasure actually removed, by collection.
     *
     * <p>Counts, never content. This is the difference between a record saying "we deleted it" and one that can show
     * what that meant, and it costs nothing to keep — {@code {"allergy": 3, "report": 12}} names no patient and
     * discloses no diagnosis.</p>
     */
    @Field("erased_counts")
    private Map<String, Long> erasedCounts;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    /** Whether this request is still owed anything. The only state in which it is. */
    public boolean isPending() {
        return DeletionRequestStatus.PENDING.equals(this.status);
    }

    public String getId() {
        return this.id;
    }

    public DeletionRequest id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public DeletionRequest patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getRequestedByEmail() {
        return this.requestedByEmail;
    }

    public DeletionRequest requestedByEmail(String requestedByEmail) {
        this.setRequestedByEmail(requestedByEmail);
        return this;
    }

    public void setRequestedByEmail(String requestedByEmail) {
        this.requestedByEmail = requestedByEmail;
    }

    public String getRequestedByLogin() {
        return this.requestedByLogin;
    }

    public DeletionRequest requestedByLogin(String requestedByLogin) {
        this.setRequestedByLogin(requestedByLogin);
        return this;
    }

    public void setRequestedByLogin(String requestedByLogin) {
        this.requestedByLogin = requestedByLogin;
    }

    public DeletionRequestStatus getStatus() {
        return this.status;
    }

    public DeletionRequest status(DeletionRequestStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(DeletionRequestStatus status) {
        this.status = status;
    }

    public String getReason() {
        return this.reason;
    }

    public DeletionRequest reason(String reason) {
        this.setReason(reason);
        return this;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getRequestedAt() {
        return this.requestedAt;
    }

    public DeletionRequest requestedAt(Instant requestedAt) {
        this.setRequestedAt(requestedAt);
        return this;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getDueAt() {
        return this.dueAt;
    }

    public DeletionRequest dueAt(Instant dueAt) {
        this.setDueAt(dueAt);
        return this;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getCancelledAt() {
        return this.cancelledAt;
    }

    public DeletionRequest cancelledAt(Instant cancelledAt) {
        this.setCancelledAt(cancelledAt);
        return this;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public DeletionRequest completedAt(Instant completedAt) {
        this.setCompletedAt(completedAt);
        return this;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCompletedByLogin() {
        return this.completedByLogin;
    }

    public DeletionRequest completedByLogin(String completedByLogin) {
        this.setCompletedByLogin(completedByLogin);
        return this;
    }

    public void setCompletedByLogin(String completedByLogin) {
        this.completedByLogin = completedByLogin;
    }

    public Instant getRejectedAt() {
        return this.rejectedAt;
    }

    public DeletionRequest rejectedAt(Instant rejectedAt) {
        this.setRejectedAt(rejectedAt);
        return this;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectedByLogin() {
        return this.rejectedByLogin;
    }

    public DeletionRequest rejectedByLogin(String rejectedByLogin) {
        this.setRejectedByLogin(rejectedByLogin);
        return this;
    }

    public void setRejectedByLogin(String rejectedByLogin) {
        this.rejectedByLogin = rejectedByLogin;
    }

    public String getDecisionReason() {
        return this.decisionReason;
    }

    public DeletionRequest decisionReason(String decisionReason) {
        this.setDecisionReason(decisionReason);
        return this;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Map<String, Long> getErasedCounts() {
        return this.erasedCounts;
    }

    public DeletionRequest erasedCounts(Map<String, Long> erasedCounts) {
        this.setErasedCounts(erasedCounts);
        return this;
    }

    public void setErasedCounts(Map<String, Long> erasedCounts) {
        this.erasedCounts = erasedCounts;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeletionRequest)) {
            return false;
        }
        return getId() != null && getId().equals(((DeletionRequest) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DeletionRequest{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", requestedByEmail='" + getRequestedByEmail() + "'" +
            ", requestedByLogin='" + getRequestedByLogin() + "'" +
            ", status='" + getStatus() + "'" +
            ", requestedAt='" + getRequestedAt() + "'" +
            ", dueAt='" + getDueAt() + "'" +
            ", cancelledAt='" + getCancelledAt() + "'" +
            ", completedAt='" + getCompletedAt() + "'" +
            ", completedByLogin='" + getCompletedByLogin() + "'" +
            ", rejectedAt='" + getRejectedAt() + "'" +
            ", rejectedByLogin='" + getRejectedByLogin() + "'" +
            ", decisionReason='" + getDecisionReason() + "'" +
            ", erasedCounts='" + getErasedCounts() + "'" +
            "}";
    }
}
