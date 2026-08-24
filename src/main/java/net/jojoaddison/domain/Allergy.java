package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.domain.enumeration.AllergyCategory;
import net.jojoaddison.domain.enumeration.AllergySeverity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Something the patient reacts to, how badly, and what the reaction looked like.
 */
@Schema(description = "Something the patient reacts to, how badly, and what the reaction looked like.")
@Document(collection = "allergy")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Allergy implements Serializable, Archivable {

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

    @Field("name")
    private String name;

    @Field("category")
    private AllergyCategory category;

    @Field("severity")
    private AllergySeverity severity;

    @Field("reaction")
    private String reaction;

    @Field("noted_on")
    private LocalDate notedOn;

    @Field("noted_by_id")
    private String notedById;

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

    public Allergy id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Allergy patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getName() {
        return this.name;
    }

    public Allergy name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AllergyCategory getCategory() {
        return this.category;
    }

    public Allergy category(AllergyCategory category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(AllergyCategory category) {
        this.category = category;
    }

    public AllergySeverity getSeverity() {
        return this.severity;
    }

    public Allergy severity(AllergySeverity severity) {
        this.setSeverity(severity);
        return this;
    }

    public void setSeverity(AllergySeverity severity) {
        this.severity = severity;
    }

    public String getReaction() {
        return this.reaction;
    }

    public Allergy reaction(String reaction) {
        this.setReaction(reaction);
        return this;
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }

    public LocalDate getNotedOn() {
        return this.notedOn;
    }

    public Allergy notedOn(LocalDate notedOn) {
        this.setNotedOn(notedOn);
        return this;
    }

    public void setNotedOn(LocalDate notedOn) {
        this.notedOn = notedOn;
    }

    public String getNotedById() {
        return this.notedById;
    }

    public Allergy notedById(String notedById) {
        this.setNotedById(notedById);
        return this;
    }

    public void setNotedById(String notedById) {
        this.notedById = notedById;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Allergy createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Allergy modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Allergy createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Allergy modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public ActivitySource getSource() {
        return this.source;
    }

    public Allergy source(ActivitySource source) {
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
        if (!(o instanceof Allergy)) {
            return false;
        }
        return getId() != null && getId().equals(((Allergy) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Allergy{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", name='" + getName() + "'" +
            ", category='" + getCategory() + "'" +
            ", severity='" + getSeverity() + "'" +
            ", reaction='" + getReaction() + "'" +
            ", notedOn='" + getNotedOn() + "'" +
            ", notedById='" + getNotedById() + "'" +
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
