package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Recommendation.
 */
@Document(collection = "recommendation")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Recommendation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("label")
    private String label;

    @Field("category")
    private String category;

    /**
     * The cases this recommendation has been attached to — <b>never serialized, in either
     * direction.</b>
     *
     * <p>{@code Recommendation} is reference data: a catalogue of labels like "HbA1c blood test",
     * shared across every patient, which is why {@code RecommendationResource} has no
     * {@code PatientScope} and its two GETs are readable by any authenticated caller, exactly as
     * {@code Team}, {@code Professional} and {@code DutyRoster} are. That posture is right and this
     * field was the one thing making it unsafe.</p>
     *
     * <p>This is the inverse side of the many-to-many. The owning side is
     * {@code ClinicalCase.recommendations}, which is patient-scoped and guarded; this side is
     * populated by nothing in this service. But it is a mapped field on an <b>unguarded</b>
     * endpoint, and a {@code ClinicalCase} carries {@code patientId}, a title and clinical notes —
     * so the day anything writes it, {@code GET /api/recommendations} starts handing every
     * authenticated patient a list of other patients' cases. There is a write path today:
     * {@code POST}/{@code PUT} on this resource take a whole {@code Recommendation} body, and a
     * clinical caller controls it.</p>
     *
     * <p>{@code @JsonIgnoreProperties} was not enough and is the trap worth naming. It suppresses
     * the nested {@code recommendations} field on each case — it exists to stop the recursion — and
     * leaves the {@code ClinicalCase} objects themselves fully serialized. It reads like a
     * disclosure control and is a cycle-breaker.</p>
     *
     * <p>Nothing is lost by hiding it. The relationship is navigable from the case, which is where
     * a caller who may see it already is, and no client reads or writes this side: the portal's
     * case detail and the generated case form both use {@code case.recommendations}. Measured on
     * quality 2026-08-31 — 39 recommendations, every {@code clinicalCases} array empty.</p>
     *
     * <p><b>Regenerating this entity drops this annotation.</b> {@code patient.jdl} declares
     * {@code ClinicalCase{recommendation} to Recommendation{clinicalCase}} and
     * {@code .jhipster/Recommendation.json} records this as the {@code right} side, so the generator
     * will write {@code @JsonIgnoreProperties} back over it and the endpoint will start disclosing
     * again — silently, because nothing populates the field in a normal environment and the
     * integration test that guards it has to write one itself. Same class of hazard as
     * {@code DutyRoster.subscribedProfessionalIds}, which the generator drops for its own reason.
     * If this entity is ever regenerated, re-apply this and check
     * {@code RecommendationDisclosureIT} still passes.</p>
     */
    @DBRef
    @Field("clinicalCases")
    @JsonIgnore
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

    public void setClinicalCases(Set<ClinicalCase> clinicalCases) {
        if (this.clinicalCases != null) {
            this.clinicalCases.forEach(i -> i.removeRecommendation(this));
        }
        if (clinicalCases != null) {
            clinicalCases.forEach(i -> i.addRecommendation(this));
        }
        this.clinicalCases = clinicalCases;
    }

    public Recommendation clinicalCases(Set<ClinicalCase> clinicalCases) {
        this.setClinicalCases(clinicalCases);
        return this;
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
