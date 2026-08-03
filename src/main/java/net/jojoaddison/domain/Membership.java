package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The patient's subscription to a care plan, and the dates it runs between.
 */
@Schema(description = "The patient's subscription to a care plan, and the dates it runs between.")
@Document(collection = "membership")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Membership implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("status")
    private String status;

    @Field("member_number")
    private String memberNumber;

    @Field("plan")
    private String plan;

    @Field("start_date")
    private LocalDate startDate;

    @Field("renewal_date")
    private LocalDate renewalDate;

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

    public Membership id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public Membership patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getName() {
        return this.name;
    }

    public Membership name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Membership description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return this.status;
    }

    public Membership status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMemberNumber() {
        return this.memberNumber;
    }

    public Membership memberNumber(String memberNumber) {
        this.setMemberNumber(memberNumber);
        return this;
    }

    public void setMemberNumber(String memberNumber) {
        this.memberNumber = memberNumber;
    }

    public String getPlan() {
        return this.plan;
    }

    public Membership plan(String plan) {
        this.setPlan(plan);
        return this;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public Membership startDate(LocalDate startDate) {
        this.setStartDate(startDate);
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getRenewalDate() {
        return this.renewalDate;
    }

    public Membership renewalDate(LocalDate renewalDate) {
        this.setRenewalDate(renewalDate);
        return this;
    }

    public void setRenewalDate(LocalDate renewalDate) {
        this.renewalDate = renewalDate;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public Membership createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public Membership modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Membership createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Membership modifiedBy(String modifiedBy) {
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
        if (!(o instanceof Membership)) {
            return false;
        }
        return getId() != null && getId().equals(((Membership) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Membership{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", status='" + getStatus() + "'" +
            ", memberNumber='" + getMemberNumber() + "'" +
            ", plan='" + getPlan() + "'" +
            ", startDate='" + getStartDate() + "'" +
            ", renewalDate='" + getRenewalDate() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
