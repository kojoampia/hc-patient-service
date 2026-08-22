package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.CaseStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A clinical episode: what the patient reported, what was found, and what was recommended. caseNumber is the human-facing reference (\"Case 12\"); brief is the one-line summary shown under the title.
 */
@Schema(
    description = "A clinical episode: what the patient reported, what was found, and what was recommended. caseNumber is the human-facing reference (\"Case 12\"); brief is the one-line summary shown under the title."
)
@Document(collection = "clinicalcase")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ClinicalCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("case_number")
    private Integer caseNumber;

    @Field("title")
    private String title;

    @Field("opened_at")
    private Instant openedAt;

    @Field("closed_at")
    private Instant closedAt;

    @Field("brief")
    private String brief;

    @Field("status")
    private CaseStatus status;

    @Field("symptoms")
    private String symptoms;

    @Field("diagnosis")
    private String diagnosis;

    @Field("assigned_professional_id")
    private String assignedProfessionalId;

    @Field("assigned_roster_id")
    private String assignedRosterId;

    /**
     * When this case was retired from the working queue, or null while it is still live.
     *
     * <p>Nullable-instant rather than a boolean, and the difference is the point: patient data is never deleted, so
     * archiving is the only way a case ever leaves a clinician's list, and "when" is the question asked about it
     * afterwards. A boolean would record that it happened and lose everything about it.</p>
     */
    @Field("archived_at")
    private Instant archivedAt;

    /** The professional who archived it. Stamped from the caller, never from the payload. */
    @Field("archived_by_id")
    private String archivedById;

    /**
     * Why it was archived.
     *
     * <p>Required by the endpoint rather than by the document: records written before archiving existed have none,
     * and a validation annotation here would make every one of them unsaveable.</p>
     */
    @Field("archive_reason")
    private String archiveReason;

    @DBRef
    @Field("recommendations")
    @JsonIgnoreProperties(value = { "clinicalCases" }, allowSetters = true)
    private Set<Recommendation> recommendations = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public ClinicalCase id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public ClinicalCase patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public Integer getCaseNumber() {
        return this.caseNumber;
    }

    public ClinicalCase caseNumber(Integer caseNumber) {
        this.setCaseNumber(caseNumber);
        return this;
    }

    public void setCaseNumber(Integer caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getTitle() {
        return this.title;
    }

    public ClinicalCase title(String title) {
        this.setTitle(title);
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getOpenedAt() {
        return this.openedAt;
    }

    public ClinicalCase openedAt(Instant openedAt) {
        this.setOpenedAt(openedAt);
        return this;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt() {
        return this.closedAt;
    }

    public ClinicalCase closedAt(Instant closedAt) {
        this.setClosedAt(closedAt);
        return this;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getBrief() {
        return this.brief;
    }

    public ClinicalCase brief(String brief) {
        this.setBrief(brief);
        return this;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public CaseStatus getStatus() {
        return this.status;
    }

    public ClinicalCase status(CaseStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public String getSymptoms() {
        return this.symptoms;
    }

    public ClinicalCase symptoms(String symptoms) {
        this.setSymptoms(symptoms);
        return this;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getDiagnosis() {
        return this.diagnosis;
    }

    public ClinicalCase diagnosis(String diagnosis) {
        this.setDiagnosis(diagnosis);
        return this;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getAssignedProfessionalId() {
        return this.assignedProfessionalId;
    }

    public ClinicalCase assignedProfessionalId(String assignedProfessionalId) {
        this.setAssignedProfessionalId(assignedProfessionalId);
        return this;
    }

    public void setAssignedProfessionalId(String assignedProfessionalId) {
        this.assignedProfessionalId = assignedProfessionalId;
    }

    public String getAssignedRosterId() {
        return this.assignedRosterId;
    }

    public ClinicalCase assignedRosterId(String assignedRosterId) {
        this.setAssignedRosterId(assignedRosterId);
        return this;
    }

    public void setAssignedRosterId(String assignedRosterId) {
        this.assignedRosterId = assignedRosterId;
    }

    public Instant getArchivedAt() {
        return this.archivedAt;
    }

    public ClinicalCase archivedAt(Instant archivedAt) {
        this.setArchivedAt(archivedAt);
        return this;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getArchivedById() {
        return this.archivedById;
    }

    public ClinicalCase archivedById(String archivedById) {
        this.setArchivedById(archivedById);
        return this;
    }

    public void setArchivedById(String archivedById) {
        this.archivedById = archivedById;
    }

    public String getArchiveReason() {
        return this.archiveReason;
    }

    public ClinicalCase archiveReason(String archiveReason) {
        this.setArchiveReason(archiveReason);
        return this;
    }

    public void setArchiveReason(String archiveReason) {
        this.archiveReason = archiveReason;
    }

    /**
     * Whether this case has been retired from the working queue.
     *
     * <p>{@code @JsonIgnore} because it is derived — serialising it would put a second, redundant answer on the wire
     * next to {@code archivedAt}, and a client could then read one and write the other.</p>
     */
    @JsonIgnore
    public boolean isArchived() {
        return this.archivedAt != null;
    }

    public Set<Recommendation> getRecommendations() {
        return this.recommendations;
    }

    public void setRecommendations(Set<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }

    public ClinicalCase recommendations(Set<Recommendation> recommendations) {
        this.setRecommendations(recommendations);
        return this;
    }

    public ClinicalCase addRecommendation(Recommendation recommendation) {
        this.recommendations.add(recommendation);
        return this;
    }

    public ClinicalCase removeRecommendation(Recommendation recommendation) {
        this.recommendations.remove(recommendation);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClinicalCase)) {
            return false;
        }
        return getId() != null && getId().equals(((ClinicalCase) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ClinicalCase{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", caseNumber=" + getCaseNumber() +
            ", title='" + getTitle() + "'" +
            ", openedAt='" + getOpenedAt() + "'" +
            ", closedAt='" + getClosedAt() + "'" +
            ", brief='" + getBrief() + "'" +
            ", status='" + getStatus() + "'" +
            ", symptoms='" + getSymptoms() + "'" +
            ", diagnosis='" + getDiagnosis() + "'" +
            ", assignedProfessionalId='" + getAssignedProfessionalId() + "'" +
            ", assignedRosterId='" + getAssignedRosterId() + "'" +
            ", archivedAt='" + getArchivedAt() + "'" +
            ", archivedById='" + getArchivedById() + "'" +
            ", archiveReason='" + getArchiveReason() + "'" +
            "}";
    }
}
