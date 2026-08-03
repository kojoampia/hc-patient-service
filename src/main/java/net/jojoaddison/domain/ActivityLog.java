package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ActivityKind;
import net.jojoaddison.domain.enumeration.ActivitySource;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One entry on the record timeline. Entries are written by professionals, by the system when something is filed, and by the patient themselves — hence `source` alongside `authorId`.
 */
@Schema(
    description = "One entry on the record timeline. Entries are written by professionals, by the system when something is filed, and by the patient themselves — hence `source` alongside `authorId`."
)
@Document(collection = "activity_log")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ActivityLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("case_id")
    private String caseId;

    @Field("logged_at")
    private Instant loggedAt;

    @Field("summary")
    private String summary;

    @Field("detail")
    private String detail;

    @Field("kind")
    private ActivityKind kind;

    @Field("source")
    private ActivitySource source;

    @Field("author_id")
    private String authorId;

    @Field("created_date")
    private LocalDate createdDate;

    @Field("created_by")
    private String createdBy;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public ActivityLog id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public ActivityLog patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getCaseId() {
        return this.caseId;
    }

    public ActivityLog caseId(String caseId) {
        this.setCaseId(caseId);
        return this;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public Instant getLoggedAt() {
        return this.loggedAt;
    }

    public ActivityLog loggedAt(Instant loggedAt) {
        this.setLoggedAt(loggedAt);
        return this;
    }

    public void setLoggedAt(Instant loggedAt) {
        this.loggedAt = loggedAt;
    }

    public String getSummary() {
        return this.summary;
    }

    public ActivityLog summary(String summary) {
        this.setSummary(summary);
        return this;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetail() {
        return this.detail;
    }

    public ActivityLog detail(String detail) {
        this.setDetail(detail);
        return this;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public ActivityKind getKind() {
        return this.kind;
    }

    public ActivityLog kind(ActivityKind kind) {
        this.setKind(kind);
        return this;
    }

    public void setKind(ActivityKind kind) {
        this.kind = kind;
    }

    public ActivitySource getSource() {
        return this.source;
    }

    public ActivityLog source(ActivitySource source) {
        this.setSource(source);
        return this;
    }

    public void setSource(ActivitySource source) {
        this.source = source;
    }

    public String getAuthorId() {
        return this.authorId;
    }

    public ActivityLog authorId(String authorId) {
        this.setAuthorId(authorId);
        return this;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public ActivityLog createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public ActivityLog createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ActivityLog)) {
            return false;
        }
        return getId() != null && getId().equals(((ActivityLog) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ActivityLog{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", caseId='" + getCaseId() + "'" +
            ", loggedAt='" + getLoggedAt() + "'" +
            ", summary='" + getSummary() + "'" +
            ", detail='" + getDetail() + "'" +
            ", kind='" + getKind() + "'" +
            ", source='" + getSource() + "'" +
            ", authorId='" + getAuthorId() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            "}";
    }
}
