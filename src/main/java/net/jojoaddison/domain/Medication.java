package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.domain.enumeration.MedicationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A drug prescribed against a case. `dosage` is the instruction as the patient reads it (\"Twice daily with food\"), which is what the medications screen shows.
 */
@Schema(
    description = "A drug prescribed against a case. `dosage` is the instruction as the patient reads it (\"Twice daily with food\"), which is what the medications screen shows."
)
@Document(collection = "medication")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Medication implements Serializable, Archivable {

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

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("patient_id")
    private String patientId;

    @Field("case_id")
    private String caseId;

    @Field("prescription")
    private String prescription;

    @Field("dosage")
    private String dosage;

    @Field("status")
    private MedicationStatus status;

    @Field("started_on")
    private LocalDate startedOn;

    @Field("prescribed_by_id")
    private String prescribedById;

    /**
     * Who reported this.
     *
     * <p>Set by the server from the authenticated caller and <strong>never from the payload</strong> — a value the
     * client can choose is a claim, not a record. A self-reported allergy and a clinician-attested one are clinically
     * different facts that would otherwise be the same document.</p>
     *
     * <p>Null on documents written before this field existed. Rendered as unattributed rather than backfilled with a
     * guess: inventing provenance for records whose origin nobody knows would be worse than admitting it is unknown.</p>
     */
    @Field("source")
    private ActivitySource source;

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

    public Medication id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Medication name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Medication description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Medication patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getCaseId() {
        return this.caseId;
    }

    public Medication caseId(String caseId) {
        this.setCaseId(caseId);
        return this;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getPrescription() {
        return this.prescription;
    }

    public Medication prescription(String prescription) {
        this.setPrescription(prescription);
        return this;
    }

    public void setPrescription(String prescription) {
        this.prescription = prescription;
    }

    public String getDosage() {
        return this.dosage;
    }

    public Medication dosage(String dosage) {
        this.setDosage(dosage);
        return this;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public MedicationStatus getStatus() {
        return this.status;
    }

    public Medication status(MedicationStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(MedicationStatus status) {
        this.status = status;
    }

    public LocalDate getStartedOn() {
        return this.startedOn;
    }

    public Medication startedOn(LocalDate startedOn) {
        this.setStartedOn(startedOn);
        return this;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public String getPrescribedById() {
        return this.prescribedById;
    }

    public Medication prescribedById(String prescribedById) {
        this.setPrescribedById(prescribedById);
        return this;
    }

    public void setPrescribedById(String prescribedById) {
        this.prescribedById = prescribedById;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Medication createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Medication modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Medication createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Medication modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public ActivitySource getSource() {
        return this.source;
    }

    public Medication source(ActivitySource source) {
        this.setSource(source);
        return this;
    }

    public void setSource(ActivitySource source) {
        this.source = source;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Medication)) {
            return false;
        }
        return getId() != null && getId().equals(((Medication) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Medication{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", patientId='" + getPatientId() + "'" +
            ", caseId='" + getCaseId() + "'" +
            ", prescription='" + getPrescription() + "'" +
            ", dosage='" + getDosage() + "'" +
            ", status='" + getStatus() + "'" +
            ", startedOn='" + getStartedOn() + "'" +
            ", prescribedById='" + getPrescribedById() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", source='" + getSource() + "'" +
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
