package net.jojoaddison.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.EmergencySeverity;
import net.jojoaddison.domain.enumeration.EmergencyStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Emergency.
 */
@Document(collection = "emergency")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Emergency implements Serializable, Archivable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * When this record was retired from the working lists, or null while it is live.
     *
     * <p>Nullable instant rather than a boolean, so the record survives the question asked of it afterwards: who
     * retired it and why. It is also what makes existing documents correct with no migration — they have no
     * {@code archived_at} key at all, and a null match in MongoDB also matches a missing field, so every one of
     * them reads as live. Query with {@code IsNull}, never a boolean test.</p>
     */
    @Field("archived_at")
    private Instant archivedAt;

    /** The login of whoever archived it. Stamped from the caller, never accepted from a payload. */
    @Field("archived_by_id")
    private String archivedById;

    /** Required when archiving. An archive with no reason is the delete this replaces. */
    @Field("archive_reason")
    private String archiveReason;

    @Field("patient_id")
    private String patientId;

    @Field("case_id")
    private String caseId;

    @Field("raised_at")
    private Instant raisedAt;

    @Field("resolved_at")
    private Instant resolvedAt;

    @Field("brief")
    private String brief;

    @Field("detail")
    private String detail;

    @Field("severity")
    private EmergencySeverity severity;

    @Field("status")
    private EmergencyStatus status;

    @Field("outcome")
    private String outcome;

    @Field("location")
    private String location;

    @Field("respondent_id")
    private String respondentId;

    @Field("created_date")
    private LocalDate createdDate;

    @Field("modified_date")
    private LocalDate modifiedDate;

    @Field("created_by")
    private String createdBy;

    @Field("modified_by")
    private String modifiedBy;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Emergency id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Emergency patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getCaseId() {
        return this.caseId;
    }

    public Emergency caseId(String caseId) {
        this.setCaseId(caseId);
        return this;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public Instant getRaisedAt() {
        return this.raisedAt;
    }

    public Emergency raisedAt(Instant raisedAt) {
        this.setRaisedAt(raisedAt);
        return this;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getResolvedAt() {
        return this.resolvedAt;
    }

    public Emergency resolvedAt(Instant resolvedAt) {
        this.setResolvedAt(resolvedAt);
        return this;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getBrief() {
        return this.brief;
    }

    public Emergency brief(String brief) {
        this.setBrief(brief);
        return this;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public String getDetail() {
        return this.detail;
    }

    public Emergency detail(String detail) {
        this.setDetail(detail);
        return this;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public EmergencySeverity getSeverity() {
        return this.severity;
    }

    public Emergency severity(EmergencySeverity severity) {
        this.setSeverity(severity);
        return this;
    }

    public void setSeverity(EmergencySeverity severity) {
        this.severity = severity;
    }

    public EmergencyStatus getStatus() {
        return this.status;
    }

    public Emergency status(EmergencyStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(EmergencyStatus status) {
        this.status = status;
    }

    public String getOutcome() {
        return this.outcome;
    }

    public Emergency outcome(String outcome) {
        this.setOutcome(outcome);
        return this;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getLocation() {
        return this.location;
    }

    public Emergency location(String location) {
        this.setLocation(location);
        return this;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRespondentId() {
        return this.respondentId;
    }

    public Emergency respondentId(String respondentId) {
        this.setRespondentId(respondentId);
        return this;
    }

    public void setRespondentId(String respondentId) {
        this.respondentId = respondentId;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Emergency createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Emergency modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Emergency createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Emergency modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Emergency)) {
            return false;
        }
        return getId() != null && getId().equals(((Emergency) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Emergency{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", caseId='" + getCaseId() + "'" +
            ", raisedAt='" + getRaisedAt() + "'" +
            ", resolvedAt='" + getResolvedAt() + "'" +
            ", brief='" + getBrief() + "'" +
            ", detail='" + getDetail() + "'" +
            ", severity='" + getSeverity() + "'" +
            ", status='" + getStatus() + "'" +
            ", outcome='" + getOutcome() + "'" +
            ", location='" + getLocation() + "'" +
            ", respondentId='" + getRespondentId() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }

    @Override
    public Instant getArchivedAt() {
        return this.archivedAt;
    }

    @Override
    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    @Override
    public String getArchivedById() {
        return this.archivedById;
    }

    @Override
    public void setArchivedById(String archivedById) {
        this.archivedById = archivedById;
    }

    @Override
    public String getArchiveReason() {
        return this.archiveReason;
    }

    @Override
    public void setArchiveReason(String archiveReason) {
        this.archiveReason = archiveReason;
    }
}
