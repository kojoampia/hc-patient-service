package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Recommendation.
 *
 * <p>The other side of {@link ClinicalCase}'s many-to-many. It exists because the replacement for {@code MedCase}
 * models recommendations as their own labelled, categorised records rather than the free-text field the old entity
 * carried. Defined by {@code hc-professional/web/.jhipster/Recommendation.json}.</p>
 */
@Document(collection = "recommendation")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Recommendation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("label")
    private String label;

    @Field("category")
    private String category;

    @DBRef
    @Field("clinicalCase")
    @JsonIgnoreProperties(value = { "recommendations" }, allowSetters = true)
    private Set<ClinicalCase> clinicalCases = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Recommendation id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return this.label;
    }

    public Recommendation label(String label) {
        this.setLabel(label);
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCategory() {
        return this.category;
    }

    public Recommendation category(String category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Set<ClinicalCase> getClinicalCases() {
        return this.clinicalCases;
    }

    public Recommendation clinicalCases(Set<ClinicalCase> clinicalCases) {
        this.setClinicalCases(clinicalCases);
        return this;
    }

    public void setClinicalCases(Set<ClinicalCase> clinicalCases) {
        if (this.clinicalCases != null) {
            this.clinicalCases.forEach(clinicalCase -> clinicalCase.removeRecommendation(this));
        }
        if (clinicalCases != null) {
            clinicalCases.forEach(clinicalCase -> clinicalCase.addRecommendation(this));
        }
        this.clinicalCases = clinicalCases;
    }

    public Recommendation addClinicalCase(ClinicalCase clinicalCase) {
        this.clinicalCases.add(clinicalCase);
        clinicalCase.getRecommendations().add(this);
        return this;
    }

    public Recommendation removeClinicalCase(ClinicalCase clinicalCase) {
        this.clinicalCases.remove(clinicalCase);
        clinicalCase.getRecommendations().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recommendation)) {
            return false;
        }
        return getId() != null && getId().equals(((Recommendation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Recommendation{" +
            "id=" + getId() +
            ", label='" + getLabel() + "'" +
            ", category='" + getCategory() + "'" +
            "}";
    }
}
