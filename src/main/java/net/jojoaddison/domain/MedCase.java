package net.jojoaddison.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.CaseCategory;
import net.jojoaddison.domain.enumeration.CaseStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A MedCase.
 */
@Document(collection = "med_case")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MedCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("symptoms")
    private String symptoms;

    @Field("diagnoses")
    private String diagnoses;

    @Field("recommendations")
    private String recommendations;

    @Field("created_date")
    private Instant createdDate;

    @Field("created_by")
    private String createdBy;

    @Field("modified_date")
    private Instant modifiedDate;

    @Field("modified_by")
    private String modifiedBy;

    @Field("status")
    private CaseStatus status;

    @Field("open_date")
    private Instant openDate;

    @Field("close_date")
    private Instant closeDate;

    @Field("category")
    private CaseCategory category;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public MedCase id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymptoms() {
        return this.symptoms;
    }

    public MedCase symptoms(String symptoms) {
        this.setSymptoms(symptoms);
        return this;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getDiagnoses() {
        return this.diagnoses;
    }

    public MedCase diagnoses(String diagnoses) {
        this.setDiagnoses(diagnoses);
        return this;
    }

    public void setDiagnoses(String diagnoses) {
        this.diagnoses = diagnoses;
    }

    public String getRecommendations() {
        return this.recommendations;
    }

    public MedCase recommendations(String recommendations) {
        this.setRecommendations(recommendations);
        return this;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public MedCase createdDate(Instant createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public MedCase createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getModifiedDate() {
        return this.modifiedDate;
    }

    public MedCase modifiedDate(Instant modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public MedCase modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public CaseStatus getStatus() {
        return this.status;
    }

    public MedCase status(CaseStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public Instant getOpenDate() {
        return this.openDate;
    }

    public MedCase openDate(Instant openDate) {
        this.setOpenDate(openDate);
        return this;
    }

    public void setOpenDate(Instant openDate) {
        this.openDate = openDate;
    }

    public Instant getCloseDate() {
        return this.closeDate;
    }

    public MedCase closeDate(Instant closeDate) {
        this.setCloseDate(closeDate);
        return this;
    }

    public void setCloseDate(Instant closeDate) {
        this.closeDate = closeDate;
    }

    public CaseCategory getCategory() {
        return this.category;
    }

    public MedCase category(CaseCategory category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(CaseCategory category) {
        this.category = category;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MedCase)) {
            return false;
        }
        return getId() != null && getId().equals(((MedCase) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "MedCase{" +
            "id=" + getId() +
            ", symptoms='" + getSymptoms() + "'" +
            ", diagnoses='" + getDiagnoses() + "'" +
            ", recommendations='" + getRecommendations() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", status='" + getStatus() + "'" +
            ", openDate='" + getOpenDate() + "'" +
            ", closeDate='" + getCloseDate() + "'" +
            ", category='" + getCategory() + "'" +
            "}";
    }
}
