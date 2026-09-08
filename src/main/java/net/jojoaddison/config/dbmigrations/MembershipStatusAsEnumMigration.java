package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Rewrites every stored {@code membership.status} onto a {@link MembershipStatus} constant.
 *
 * <p>{@code Membership.status} was a free {@code String} until the lifecycle needed a vocabulary an administrator's
 * approval could be expressed in. The values already written are few and known — the clients only ever posted
 * {@code PENDING}, and the one differing spelling is the quality seed's lower-case {@code "active"} — but the mapped
 * type now declares an enum, and Spring Data will throw converting {@code "active"} to one. This runs first and
 * leaves nothing for that conversion to trip over.</p>
 *
 * <h2>Why the idempotency criterion is not the one change unit 001 used</h2>
 *
 * <p>{@link AddressAsDocumentMigration} matches on {@code $type: 'string'}, which works there because it is changing
 * the BSON <em>type</em> — a migrated profile holds a DBRef and stops matching. <b>That would loop here.</b> Spring
 * Data stores an enum as its {@code name()}, so a migrated status is still a BSON string and {@code $type: 'string'}
 * would match every document on every run, forever.</p>
 *
 * <p>So this matches on the <em>value</em> instead: strings that are not already one of the constants. After a pass
 * every remaining value is canonical, so a second run selects nothing. That is what makes it safe to repeat, which
 * matters for the same reason it did in 001 — there is no transaction, so a partial run is a state this has to be
 * resumable from.</p>
 *
 * <h2>An unrecognised value is set aside, not guessed at</h2>
 *
 * <p>Anything that resolves to no constant has its {@code status} unset and the original kept under
 * {@code status_unrecognised}. Guessing is the one thing that must not happen: reading a stray value as
 * {@code ACTIVE} would hand somebody a subscription they were never sold, and reading it as {@code CANCELLED} would
 * take one away. An unset status renders as an em dash on the patient's screen and leaves the record readable, which
 * is the failure {@link net.jojoaddison.domain.enumeration.IdentificationType} warns about avoided by a different
 * route — there the value is kept because it cannot be enumerated, here it is enumerable and so what does not match
 * is genuinely wrong rather than merely untidy.</p>
 *
 * <p>Be honest about what that keeps: {@code status_unrecognised} is not mapped on {@code Membership}, so the next
 * write through {@code MembershipResource} rewrites the document without it. It is a forensic aid for whoever reads
 * the logged warning, not a durable second record.</p>
 *
 * <h2>It runs wherever the application runs</h2>
 *
 * <p>A change unit has no notion of a Spring profile — 001 says so, and the gateway once shipped derived-password
 * logins to production by forgetting it. That is the right behaviour here: reshaping data that must happen in
 * <em>every</em> environment is exactly what the mechanism is for. Note this is why the quality seed still had to be
 * hand-edited to {@code "ACTIVE"}: {@code DevelopmentDataInitializer} binds that file straight into the domain class
 * through Jackson, and a document read from outside the database is never seen by a migration.</p>
 */
@ChangeUnit(id = "membership-status-to-enum", order = "002", author = "hc-patient")
public class MembershipStatusAsEnumMigration {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipStatusAsEnumMigration.class);

    private static final String MEMBERSHIP = "membership";
    private static final String STATUS = "status";
    private static final String UNRECOGNISED = "status_unrecognised";

    /** BSON type 2 — a UTF-8 string. Spelled as the number because that is what {@link Criteria#type(int)} takes. */
    private static final int BSON_STRING = 2;

    private final MongoTemplate mongoTemplate;

    public MembershipStatusAsEnumMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        List<Document> memberships = selectNotCanonical();

        if (memberships.isEmpty()) {
            LOG.debug("No membership statuses to migrate");
            return;
        }

        int migrated = 0;
        int setAside = 0;
        for (Document membership : memberships) {
            Object id = membership.get("_id");
            String stored = membership.getString(STATUS);
            Optional<MembershipStatus> recognised = MembershipStatus.from(stored);

            Update update;
            if (recognised.isPresent()) {
                update = new Update().set(STATUS, recognised.orElseThrow().name());
                migrated++;
            } else {
                // Unset rather than guessed at, and the original kept for whoever reads the warning.
                update = new Update().unset(STATUS).set(UNRECOGNISED, stored);
                setAside++;
                LOG.warn("Membership {} carries the unrecognised status '{}'; setting it aside under {}", id, stored, UNRECOGNISED);
            }
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)), update, MEMBERSHIP);
        }
        LOG.info("Migrated {} membership status(es) onto MembershipStatus; set {} unrecognised value(s) aside", migrated, setAside);
    }

    /**
     * The documents this migration still has work to do on.
     *
     * <p>Extracted so a test can assert it is <b>empty on a second pass</b>, which is the only assertion that pins the
     * criterion. Comparing the documents before and after a rerun cannot: with a {@code $type}-only criterion the rerun
     * re-selects every migrated document and sets {@code ACTIVE} over {@code ACTIVE}, so the document is byte-identical
     * and an equality assertion passes while the migration churns the whole collection on every start for ever. <b>The
     * hazard is re-selection, so re-selection is what has to be observed.</b></p>
     *
     * <p>{@code $type} alone is not enough because an enum is stored as a string like any other; {@code $nin} over the
     * constants is what excludes the already-migrated. Both halves are load-bearing.</p>
     */
    List<Document> selectNotCanonical() {
        List<String> canonical = Arrays.stream(MembershipStatus.values()).map(Enum::name).toList();
        Query notCanonical = new Query(Criteria.where(STATUS).type(BSON_STRING).nin(canonical));
        return mongoTemplate.find(notCanonical, Document.class, MEMBERSHIP);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>The same answer 001 gives, and for the same reason: rolling back means restoring the database. Putting the
     * old casings back would be guesswork about which of them a record started with, and by then an administrator may
     * have approved a membership through the typed field — a rollback would silently discard that decision.</p>
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("membership-status-to-enum is not rolled back automatically — see the class comment");
    }
}
