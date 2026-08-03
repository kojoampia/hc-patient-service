package net.jojoaddison.domain;

import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A PersonalDocument.
 */
@Document(collection = "personal_document")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PersonalDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("category")
    private String category;

    @Field("url")
    private String url;

    @Field("patient_id")
    private String patientId;

    @Field("issued_on")
    private LocalDate issuedOn;

    @Field("expires_on")
    private LocalDate expiresOn;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public PersonalDocument id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public PersonalDocument name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return this.category;
    }

    public PersonalDocument category(String category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUrl() {
        return this.url;
    }

    public PersonalDocument url(String url) {
        this.setUrl(url);
        return this;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public PersonalDocument patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public LocalDate getIssuedOn() {
        return this.issuedOn;
    }

    public PersonalDocument issuedOn(LocalDate issuedOn) {
        this.setIssuedOn(issuedOn);
        return this;
    }

    public void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public LocalDate getExpiresOn() {
        return this.expiresOn;
    }

    public PersonalDocument expiresOn(LocalDate expiresOn) {
        this.setExpiresOn(expiresOn);
        return this;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PersonalDocument)) {
            return false;
        }
        return getId() != null && getId().equals(((PersonalDocument) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PersonalDocument{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", category='" + getCategory() + "'" +
            ", url='" + getUrl() + "'" +
            ", patientId='" + getPatientId() + "'" +
            ", issuedOn='" + getIssuedOn() + "'" +
            ", expiresOn='" + getExpiresOn() + "'" +
            "}";
    }
}
