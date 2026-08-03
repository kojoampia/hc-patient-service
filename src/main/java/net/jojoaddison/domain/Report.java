package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A lab, imaging, clinical or immunisation report filed against a case. `summary` is the plain-language reading shown to the patient; `description` stays free-form clinical text.
 */
@Schema(
    description = "A lab, imaging, clinical or immunisation report filed against a case. `summary` is the plain-language reading shown to the patient; `description` stays free-form clinical text."
)
@Document(collection = "report")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("category")
    private String category;

    @Field("description")
    private String description;

    @Field("summary")
    private String summary;

    @Field("name")
    private String name;

    @Field("url")
    private String url;

    @Field("patient_id")
    private String patientId;

    @Field("case_id")
    private String caseId;

    @Field("author_id")
    private String authorId;

    @Field("report_date")
    private LocalDate reportDate;

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

    public Report id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return this.category;
    }

    public Report category(String category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return this.description;
    }

    public Report description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSummary() {
        return this.summary;
    }

    public Report summary(String summary) {
        this.setSummary(summary);
        return this;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getName() {
        return this.name;
    }

    public Report name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return this.url;
    }

    public Report url(String url) {
        this.setUrl(url);
        return this;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Report patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getCaseId() {
        return this.caseId;
    }

    public Report caseId(String caseId) {
        this.setCaseId(caseId);
        return this;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getAuthorId() {
        return this.authorId;
    }

    public Report authorId(String authorId) {
        this.setAuthorId(authorId);
        return this;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public LocalDate getReportDate() {
        return this.reportDate;
    }

    public Report reportDate(LocalDate reportDate) {
        this.setReportDate(reportDate);
        return this;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Report createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Report modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Report createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Report modifiedBy(String modifiedBy) {
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
        if (!(o instanceof Report)) {
            return false;
        }
        return getId() != null && getId().equals(((Report) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Report{" +
            "id=" + getId() +
            ", category='" + getCategory() + "'" +
            ", description='" + getDescription() + "'" +
            ", summary='" + getSummary() + "'" +
            ", name='" + getName() + "'" +
            ", url='" + getUrl() + "'" +
            ", patientId='" + getPatientId() + "'" +
            ", caseId='" + getCaseId() + "'" +
            ", authorId='" + getAuthorId() + "'" +
            ", reportDate='" + getReportDate() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
