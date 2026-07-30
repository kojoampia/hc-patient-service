package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
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
 * A ClinicalCase.
 *
 * <p>Replaces the former {@code MedCase}, whose shape it does not share. The contract is the one the professional
 * dashboard generates against ({@code hc-professional/web/.jhipster/ClinicalCase.json}): a case now carries who it
 * is about and who it is assigned to ({@code patientId}, {@code assignedProfessionalId}, {@code assignedRosterId})
 * plus a short {@code brief} for queue rows, and {@code recommendations} became a relationship to
 * {@link Recommendation} instead of a free-text field. {@code MedCase}'s {@code closeDate}, {@code category} and
 * audit fields are gone.</p>
 */
@Document(collection = "clinicalcase")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ClinicalCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("opened_at")
    private Instant openedAt;

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

    @DBRef
    @Field("recommendation")
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

    public Set<Recommendation> getRecommendations() {
        return this.recommendations;
    }

    public ClinicalCase recommendations(Set<Recommendation> recommendations) {
        this.setRecommendations(recommendations);
        return this;
    }

    public void setRecommendations(Set<Recommendation> recommendations) {
        this.recommendations = recommendations;
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
            ", openedAt='" + getOpenedAt() + "'" +
            ", brief='" + getBrief() + "'" +
            ", status='" + getStatus() + "'" +
            ", symptoms='" + getSymptoms() + "'" +
            ", diagnosis='" + getDiagnosis() + "'" +
            ", assignedProfessionalId='" + getAssignedProfessionalId() + "'" +
            ", assignedRosterId='" + getAssignedRosterId() + "'" +
            "}";
    }
}
