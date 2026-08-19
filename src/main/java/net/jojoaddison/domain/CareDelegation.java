package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.DelegationParty;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One person's authority to act for one patient.
 *
 * <h2>This document is the authorization</h2>
 *
 * <p>{@code ROLE_ANGEL} grants nothing. An {@code ACTIVE} row here grants everything, and
 * {@link net.jojoaddison.security.PatientScope} reads this collection — never {@code Profile.careAngelEmail}, which is
 * a display cache — when deciding whether one person may act for another. Two things follow that are worth having
 * deliberately: a revocation takes effect on the very next request, because nothing is baked into a token that would
 * have to expire first; and no code has to remember to strip a role when a delegation ends.</p>
 *
 * <h2>Two ways in, and neither is quick</h2>
 *
 * <p>The ordinary path is that a patient nominates someone ({@code PENDING}) who accepts ({@code ACTIVE}).</p>
 *
 * <p>The other exists for the case the whole arrangement is for — a patient who is incapacitated and never got round
 * to nominating anyone. They can name a {@code STANDBY} nominee during onboarding and consent, in advance and while
 * they are able to, that clinicians may activate them. Ripening it takes two <em>different</em> professionals, and it
 * still ends with the nominee accepting. Notably this gives nobody a new power to <em>create</em> a delegation: a
 * professional can only ripen one the patient already authorised.</p>
 *
 * <h2>Nothing is deleted</h2>
 *
 * <p>Ending a delegation records who ended it and when. {@code DECLINED} and {@code REVOKED} are terminal, and
 * re-nominating the same person creates a new row rather than reopening an old one, so the history of who could act
 * for this patient — and between which dates — stays readable.</p>
 */
@Schema(description = "One person's authority to act for one patient.")
@Document(collection = "caredelegation")
@CompoundIndexes(
    {
        // The authorization lookup, run on every request that carries an X-Acting-As header. It is the reason this
        // index is not optional.
        @CompoundIndex(name = "cd_angel_status", def = "{'angel_email': 1, 'status': 1}"),
        @CompoundIndex(name = "cd_patient_status", def = "{'patient_id': 1, 'status': 1}"),
    }
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CareDelegation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("patient_id")
    private String patientId;

    /** The angel's identity, matched case-insensitively against the token's email claim. */
    @Field("angel_email")
    private String angelEmail;

    @Field("angel_login")
    private String angelLogin;

    @Field("angel_name")
    private String angelName;

    @Field("angel_phone")
    private String angelPhone;

    @Field("status")
    private DelegationStatus status;

    @Field("granted_at")
    private Instant grantedAt;

    @Field("accepted_at")
    private Instant acceptedAt;

    @Field("revoked_at")
    private Instant revokedAt;

    @Field("revoked_by")
    private DelegationParty revokedBy;

    /**
     * The patient's recorded authorisation for the standby path.
     *
     * <p>This is the only evidence the patient ever agreed to a clinician activating this nominee, so activation must
     * be refused without it. Enforcing it in the browser alone would leave the authorisation existing nowhere.</p>
     */
    @Field("advance_consent")
    private Boolean advanceConsent;

    @Field("activation_requested_by_id")
    private String activationRequestedById;

    @Field("activation_requested_at")
    private Instant activationRequestedAt;

    /** The incapacity declaration, stored rather than merely logged. */
    @Field("activation_reason")
    private String activationReason;

    /**
     * The second professional.
     *
     * <p>Must differ from {@link #activationRequestedById}. That single comparison is the whole of the two-signature
     * control — everything else on the standby path is bookkeeping, and if this check is wrong the control does not
     * exist at all.</p>
     */
    @Field("countersigned_by_id")
    private String countersignedById;

    @Field("countersigned_at")
    private Instant countersignedAt;

    @Field("created_date")
    private LocalDate createdDate;

    @Field("modified_date")
    private LocalDate modifiedDate;

    @Field("created_by")
    private String createdBy;

    @Field("modified_by")
    private String modifiedBy;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    /** Whether this delegation confers access right now. The only state that does. */
    public boolean isActive() {
        return DelegationStatus.ACTIVE.equals(this.status);
    }

    public String getId() {
        return this.id;
    }

    public CareDelegation id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public CareDelegation patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getAngelEmail() {
        return this.angelEmail;
    }

    public CareDelegation angelEmail(String angelEmail) {
        this.setAngelEmail(angelEmail);
        return this;
    }

    public void setAngelEmail(String angelEmail) {
        this.angelEmail = angelEmail;
    }

    public String getAngelLogin() {
        return this.angelLogin;
    }

    public CareDelegation angelLogin(String angelLogin) {
        this.setAngelLogin(angelLogin);
        return this;
    }

    public void setAngelLogin(String angelLogin) {
        this.angelLogin = angelLogin;
    }

    public String getAngelName() {
        return this.angelName;
    }

    public CareDelegation angelName(String angelName) {
        this.setAngelName(angelName);
        return this;
    }

    public void setAngelName(String angelName) {
        this.angelName = angelName;
    }

    public String getAngelPhone() {
        return this.angelPhone;
    }

    public CareDelegation angelPhone(String angelPhone) {
        this.setAngelPhone(angelPhone);
        return this;
    }

    public void setAngelPhone(String angelPhone) {
        this.angelPhone = angelPhone;
    }

    public DelegationStatus getStatus() {
        return this.status;
    }

    public CareDelegation status(DelegationStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(DelegationStatus status) {
        this.status = status;
    }

    public Instant getGrantedAt() {
        return this.grantedAt;
    }

    public CareDelegation grantedAt(Instant grantedAt) {
        this.setGrantedAt(grantedAt);
        return this;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public Instant getAcceptedAt() {
        return this.acceptedAt;
    }

    public CareDelegation acceptedAt(Instant acceptedAt) {
        this.setAcceptedAt(acceptedAt);
        return this;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public CareDelegation revokedAt(Instant revokedAt) {
        this.setRevokedAt(revokedAt);
        return this;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public DelegationParty getRevokedBy() {
        return this.revokedBy;
    }

    public CareDelegation revokedBy(DelegationParty revokedBy) {
        this.setRevokedBy(revokedBy);
        return this;
    }

    public void setRevokedBy(DelegationParty revokedBy) {
        this.revokedBy = revokedBy;
    }

    public Boolean getAdvanceConsent() {
        return this.advanceConsent;
    }

    public CareDelegation advanceConsent(Boolean advanceConsent) {
        this.setAdvanceConsent(advanceConsent);
        return this;
    }

    public void setAdvanceConsent(Boolean advanceConsent) {
        this.advanceConsent = advanceConsent;
    }

    public String getActivationRequestedById() {
        return this.activationRequestedById;
    }

    public CareDelegation activationRequestedById(String activationRequestedById) {
        this.setActivationRequestedById(activationRequestedById);
        return this;
    }

    public void setActivationRequestedById(String activationRequestedById) {
        this.activationRequestedById = activationRequestedById;
    }

    public Instant getActivationRequestedAt() {
        return this.activationRequestedAt;
    }

    public CareDelegation activationRequestedAt(Instant activationRequestedAt) {
        this.setActivationRequestedAt(activationRequestedAt);
        return this;
    }

    public void setActivationRequestedAt(Instant activationRequestedAt) {
        this.activationRequestedAt = activationRequestedAt;
    }

    public String getActivationReason() {
        return this.activationReason;
    }

    public CareDelegation activationReason(String activationReason) {
        this.setActivationReason(activationReason);
        return this;
    }

    public void setActivationReason(String activationReason) {
        this.activationReason = activationReason;
    }

    public String getCountersignedById() {
        return this.countersignedById;
    }

    public CareDelegation countersignedById(String countersignedById) {
        this.setCountersignedById(countersignedById);
        return this;
    }

    public void setCountersignedById(String countersignedById) {
        this.countersignedById = countersignedById;
    }

    public Instant getCountersignedAt() {
        return this.countersignedAt;
    }

    public CareDelegation countersignedAt(Instant countersignedAt) {
        this.setCountersignedAt(countersignedAt);
        return this;
    }

    public void setCountersignedAt(Instant countersignedAt) {
        this.countersignedAt = countersignedAt;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public CareDelegation createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public CareDelegation modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public CareDelegation createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public CareDelegation modifiedBy(String modifiedBy) {
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
        if (!(o instanceof CareDelegation)) {
            return false;
        }
        return getId() != null && getId().equals(((CareDelegation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CareDelegation{" +
            "id=" + getId() +
            ", patientId='" + getPatientId() + "'" +
            ", angelEmail='" + getAngelEmail() + "'" +
            ", angelLogin='" + getAngelLogin() + "'" +
            ", angelName='" + getAngelName() + "'" +
            ", angelPhone='" + getAngelPhone() + "'" +
            ", status='" + getStatus() + "'" +
            ", grantedAt='" + getGrantedAt() + "'" +
            ", acceptedAt='" + getAcceptedAt() + "'" +
            ", revokedAt='" + getRevokedAt() + "'" +
            ", revokedBy='" + getRevokedBy() + "'" +
            ", advanceConsent='" + getAdvanceConsent() + "'" +
            ", activationRequestedById='" + getActivationRequestedById() + "'" +
            ", activationRequestedAt='" + getActivationRequestedAt() + "'" +
            ", activationReason='" + getActivationReason() + "'" +
            ", countersignedById='" + getCountersignedById() + "'" +
            ", countersignedAt='" + getCountersignedAt() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            "}";
    }
}
