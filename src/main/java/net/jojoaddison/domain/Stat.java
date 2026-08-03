package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.StatFlag;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A single recorded measurement — this is where vitals live. Blood pressure uses `value` for the systolic reading and `secondaryValue` for the diastolic; every other vital uses `value` alone. referenceLow/referenceHigh carry the normal band the reading is judged against.
 */
@Schema(
    description = "A single recorded measurement — this is where vitals live. Blood pressure uses `value` for the systolic reading and `secondaryValue` for the diastolic; every other vital uses `value` alone. referenceLow/referenceHigh carry the normal band the reading is judged against."
)
@Document(collection = "stat")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Stat implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("type")
    private String type;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("value")
    private Double value;

    @Field("secondary_value")
    private Double secondaryValue;

    @Field("unit")
    private String unit;

    @Field("reference_low")
    private Double referenceLow;

    @Field("reference_high")
    private Double referenceHigh;

    @Field("flag")
    private StatFlag flag;

    @Field("note")
    private String note;

    @Field("recorded_at")
    private Instant recordedAt;

    @Field("created_date")
    private LocalDate createdDate;

    @Field("created_by")
    private String createdBy;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Stat id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Stat patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getType() {
        return this.type;
    }

    public Stat type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return this.name;
    }

    public Stat name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Stat description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getValue() {
        return this.value;
    }

    public Stat value(Double value) {
        this.setValue(value);
        return this;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Double getSecondaryValue() {
        return this.secondaryValue;
    }

    public Stat secondaryValue(Double secondaryValue) {
        this.setSecondaryValue(secondaryValue);
        return this;
    }

    public void setSecondaryValue(Double secondaryValue) {
        this.secondaryValue = secondaryValue;
    }

    public String getUnit() {
        return this.unit;
    }

    public Stat unit(String unit) {
        this.setUnit(unit);
        return this;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getReferenceLow() {
        return this.referenceLow;
    }

    public Stat referenceLow(Double referenceLow) {
        this.setReferenceLow(referenceLow);
        return this;
    }

    public void setReferenceLow(Double referenceLow) {
        this.referenceLow = referenceLow;
    }

    public Double getReferenceHigh() {
        return this.referenceHigh;
    }

    public Stat referenceHigh(Double referenceHigh) {
        this.setReferenceHigh(referenceHigh);
        return this;
    }

    public void setReferenceHigh(Double referenceHigh) {
        this.referenceHigh = referenceHigh;
    }

    public StatFlag getFlag() {
        return this.flag;
    }

    public Stat flag(StatFlag flag) {
        this.setFlag(flag);
        return this;
    }

    public void setFlag(StatFlag flag) {
        this.flag = flag;
    }

    public String getNote() {
        return this.note;
    }

    public Stat note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getRecordedAt() {
        return this.recordedAt;
    }

    public Stat recordedAt(Instant recordedAt) {
        this.setRecordedAt(recordedAt);
        return this;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Stat createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Stat createdBy(String createdBy) {
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
        if (!(o instanceof Stat)) {
            return false;
        }
        return getId() != null && getId().equals(((Stat) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Stat{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", type='" + getType() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", value=" + getValue() +
            ", secondaryValue=" + getSecondaryValue() +
            ", unit='" + getUnit() + "'" +
            ", referenceLow=" + getReferenceLow() +
            ", referenceHigh=" + getReferenceHigh() +
            ", flag='" + getFlag() + "'" +
            ", note='" + getNote() + "'" +
            ", recordedAt='" + getRecordedAt() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            "}";
    }
}
