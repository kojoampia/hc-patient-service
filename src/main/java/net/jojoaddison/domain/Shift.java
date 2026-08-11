package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One professional's turn on a duty roster, between two instants.
 */
@Schema(description = "One professional's turn on a duty roster, between two instants.")
@Document(collection = "shift")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Shift implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("roster_id")
    private String rosterId;

    @Field("professional_id")
    private String professionalId;

    @Field("starts_at")
    private Instant startsAt;

    @Field("ends_at")
    private Instant endsAt;

    @Field("status")
    private ShiftStatus status;

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

    public Shift id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRosterId() {
        return this.rosterId;
    }

    public Shift rosterId(String rosterId) {
        this.setRosterId(rosterId);
        return this;
    }

    public void setRosterId(String rosterId) {
        this.rosterId = rosterId;
    }

    public String getProfessionalId() {
        return this.professionalId;
    }

    public Shift professionalId(String professionalId) {
        this.setProfessionalId(professionalId);
        return this;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public Instant getStartsAt() {
        return this.startsAt;
    }

    public Shift startsAt(Instant startsAt) {
        this.setStartsAt(startsAt);
        return this;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return this.endsAt;
    }

    public Shift endsAt(Instant endsAt) {
        this.setEndsAt(endsAt);
        return this;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public ShiftStatus getStatus() {
        return this.status;
    }

    public Shift status(ShiftStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return this.notes;
    }

    public Shift notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Shift createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Shift modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Shift createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Shift modifiedBy(String modifiedBy) {
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
        if (!(o instanceof Shift)) {
            return false;
        }
        return getId() != null && getId().equals(((Shift) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Shift{" +
            "id=" + getId() +
            ", rosterId='" + getRosterId() + "'" +
            ", professionalId='" + getProfessionalId() + "'" +
            ", startsAt='" + getStartsAt() + "'" +
            ", endsAt='" + getEndsAt() + "'" +
            ", status='" + getStatus() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
