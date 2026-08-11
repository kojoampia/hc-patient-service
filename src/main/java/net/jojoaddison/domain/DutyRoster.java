package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A named rota a professional can be on duty for — a ward, a clinic, a night shift.
 */
@Schema(description = "A named rota a professional can be on duty for — a ward, a clinic, a night shift.")
@Document(collection = "dutyroster")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DutyRoster implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("location")
    private String location;

    /**
     * The professionals who follow this roster, held as plain ids in a Mongo array.
     *
     * <p><strong>This field is not in {@code patient.jdl} and will not survive a regeneration of this
     * entity</strong> — JDL has no list-of-scalars type, and the alternative, a {@code @DBRef}
     * many-to-many to {@link Professional}, would be the second relationship in a domain that holds
     * every other cross-entity reference as a bare String id (see the conventions in {@code patient.jdl}).
     * Subscription is not the same thing as being rostered: a professional subscribed here has no
     * {@link Shift} until one is assigned, and a roster can exist with neither. If this entity is ever
     * regenerated, add this field back by hand.</p>
     */
    @Field("subscribed_professional_ids")
    private Set<String> subscribedProfessionalIds = new HashSet<>();

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

    public DutyRoster id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public DutyRoster name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public DutyRoster description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return this.location;
    }

    public DutyRoster location(String location) {
        this.setLocation(location);
        return this;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Set<String> getSubscribedProfessionalIds() {
        return this.subscribedProfessionalIds;
    }

    public DutyRoster subscribedProfessionalIds(Set<String> subscribedProfessionalIds) {
        this.setSubscribedProfessionalIds(subscribedProfessionalIds);
        return this;
    }

    public void setSubscribedProfessionalIds(Set<String> subscribedProfessionalIds) {
        this.subscribedProfessionalIds = subscribedProfessionalIds == null ? new HashSet<>() : subscribedProfessionalIds;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public DutyRoster createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public DutyRoster modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public DutyRoster createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public DutyRoster modifiedBy(String modifiedBy) {
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
        if (!(o instanceof DutyRoster)) {
            return false;
        }
        return getId() != null && getId().equals(((DutyRoster) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DutyRoster{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", location='" + getLocation() + "'" +
            ", subscribedProfessionalIds='" + getSubscribedProfessionalIds() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
