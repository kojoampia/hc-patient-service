package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.CarePlanType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One line of a diet or exercise plan, with its own completion state so the patient can tick it off.
 */
@Schema(description = "One line of a diet or exercise plan, with its own completion state so the patient can tick it off.")
@Document(collection = "care_plan_item")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CarePlanItem implements Serializable, Archivable {

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

    @Field("plan_type")
    private CarePlanType planType;

    @Field("label")
    private String label;

    @Field("detail")
    private String detail;

    @Field("cadence")
    private String cadence;

    @Field("completed")
    private Boolean completed;

    @Field("sort_order")
    private Integer sortOrder;

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

    public CarePlanItem id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public CarePlanItem patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public CarePlanType getPlanType() {
        return this.planType;
    }

    public CarePlanItem planType(CarePlanType planType) {
        this.setPlanType(planType);
        return this;
    }

    public void setPlanType(CarePlanType planType) {
        this.planType = planType;
    }

    public String getLabel() {
        return this.label;
    }

    public CarePlanItem label(String label) {
        this.setLabel(label);
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDetail() {
        return this.detail;
    }

    public CarePlanItem detail(String detail) {
        this.setDetail(detail);
        return this;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCadence() {
        return this.cadence;
    }

    public CarePlanItem cadence(String cadence) {
        this.setCadence(cadence);
        return this;
    }

    public void setCadence(String cadence) {
        this.cadence = cadence;
    }

    public Boolean getCompleted() {
        return this.completed;
    }

    public CarePlanItem completed(Boolean completed) {
        this.setCompleted(completed);
        return this;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public CarePlanItem sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public CarePlanItem createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public CarePlanItem modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public CarePlanItem createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public CarePlanItem modifiedBy(String modifiedBy) {
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
        if (!(o instanceof CarePlanItem)) {
            return false;
        }
        return getId() != null && getId().equals(((CarePlanItem) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CarePlanItem{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", planType='" + getPlanType() + "'" +
            ", label='" + getLabel() + "'" +
            ", detail='" + getDetail() + "'" +
            ", cadence='" + getCadence() + "'" +
            ", completed='" + getCompleted() + "'" +
            ", sortOrder=" + getSortOrder() +
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
