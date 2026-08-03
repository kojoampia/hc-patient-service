package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A visit that took place — the counterpart to Task, which is a visit that is planned.
 */
@Schema(description = "A visit that took place — the counterpart to Task, which is a visit that is planned.")
@Document(collection = "visitation")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Visitation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("case_id")
    private String caseId;

    @Field("professional_id")
    private String professionalId;

    @Field("visited_at")
    private Instant visitedAt;

    @Field("purpose")
    private String purpose;

    @Field("location")
    private String location;

    @Field("notes")
    private String notes;

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

    public Visitation id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Visitation patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getCaseId() {
        return this.caseId;
    }

    public Visitation caseId(String caseId) {
        this.setCaseId(caseId);
        return this;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getProfessionalId() {
        return this.professionalId;
    }

    public Visitation professionalId(String professionalId) {
        this.setProfessionalId(professionalId);
        return this;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public Instant getVisitedAt() {
        return this.visitedAt;
    }

    public Visitation visitedAt(Instant visitedAt) {
        this.setVisitedAt(visitedAt);
        return this;
    }

    public void setVisitedAt(Instant visitedAt) {
        this.visitedAt = visitedAt;
    }

    public String getPurpose() {
        return this.purpose;
    }

    public Visitation purpose(String purpose) {
        this.setPurpose(purpose);
        return this;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getLocation() {
        return this.location;
    }

    public Visitation location(String location) {
        this.setLocation(location);
        return this;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return this.notes;
    }

    public Visitation notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Visitation createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Visitation modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Visitation createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Visitation modifiedBy(String modifiedBy) {
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
        if (!(o instanceof Visitation)) {
            return false;
        }
        return getId() != null && getId().equals(((Visitation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Visitation{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", caseId='" + getCaseId() + "'" +
            ", professionalId='" + getProfessionalId() + "'" +
            ", visitedAt='" + getVisitedAt() + "'" +
            ", purpose='" + getPurpose() + "'" +
            ", location='" + getLocation() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
