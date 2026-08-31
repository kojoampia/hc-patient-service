package net.jojoaddison.domain;

import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A PaymentOption.
 */
@Document(collection = "payment_option")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PaymentOption implements Serializable, Archivable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("type")
    private String type;

    @Field("user_id")
    private String userID;

    @Field("metadata")
    private String metadata;

    /**
     * When this payment option was retired, or null while it is live.
     *
     * <p>A nullable instant rather than a boolean, matching the clinical entities: the question asked about a
     * retired card afterwards is <em>who</em> and <em>why</em>, and a boolean records that it happened and loses
     * both. Query with {@code IsNull}, never a boolean test — every row written before these fields existed has no
     * {@code archived_at} key at all, and in MongoDB a null match also matches a missing field, so they all read as
     * live with no migration.</p>
     *
     * <p><b>Why this entity and not the other four administrative ones</b> (decided 2026-08-31): it is the only one
     * with no existing field that could stand in. {@code Membership} has {@code status}, {@code PersonalDocument}
     * has {@code expiresOn}, an {@code Address} a patient has moved away from is arguably history rather than
     * something archived, and ending a {@code Profile} already has a verb in {@code DeletionRequest}. An expired
     * card had nothing.</p>
     */
    @Field("archived_at")
    private Instant archivedAt;

    /** The login of whoever archived it. Stamped from the caller, never accepted from a payload. */
    @Field("archived_by_id")
    private String archivedById;

    /** Required when archiving. An archive with no reason is the delete this replaces. */
    @Field("archive_reason")
    private String archiveReason;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public PaymentOption id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return this.type;
    }

    public PaymentOption type(String type) {
        this.setType(type);
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserID() {
        return this.userID;
    }

    public PaymentOption userID(String userID) {
        this.setUserID(userID);
        return this;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getMetadata() {
        return this.metadata;
    }

    public PaymentOption metadata(String metadata) {
        this.setMetadata(metadata);
        return this;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    @Override
    public Instant getArchivedAt() {
        return this.archivedAt;
    }

    @Override
    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    @Override
    public String getArchivedById() {
        return this.archivedById;
    }

    @Override
    public void setArchivedById(String archivedById) {
        this.archivedById = archivedById;
    }

    @Override
    public String getArchiveReason() {
        return this.archiveReason;
    }

    @Override
    public void setArchiveReason(String archiveReason) {
        this.archiveReason = archiveReason;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PaymentOption)) {
            return false;
        }
        return getId() != null && getId().equals(((PaymentOption) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PaymentOption{" +
            "id=" + getId() +
            ", type='" + getType() + "'" +
            ", userID='" + getUserID() + "'" +
            ", metadata='" + getMetadata() + "'" +
            "}";
    }
}
