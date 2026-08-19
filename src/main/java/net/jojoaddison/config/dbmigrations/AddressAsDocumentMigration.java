package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Turns each profile's free-text address into a real {@code Address} document.
 *
 * <p>{@code Profile.address} was a String until care onboarding needed a structured one — a digital address, a town
 * and a region cannot be recovered from "5 Ankobra River Street" once somebody has typed it that way. Existing records
 * hold exactly that string, so this writes an {@code Address} carrying it in {@code streetAddress} and repoints the
 * profile at it. Nothing is lost, and the profile screen keeps rendering something.</p>
 *
 * <h2>The first change unit in this service, and why it is one</h2>
 *
 * <p>Mongock has been a dependency and {@code migration-scan-package} has pointed here all along; the package simply
 * held nothing but two profile-gated runners. A change unit is right for this and wrong for seeding, which is the
 * distinction that cost the gateway a production incident: a change unit has no notion of a Spring profile and runs
 * wherever the application runs, so seeding accounts from one shipped derived-password logins to production. A
 * one-time reshaping of data that must happen in <em>every</em> environment is exactly what the mechanism is for.</p>
 *
 * <h2>Written against the raw documents on purpose</h2>
 *
 * <p>Reading through {@code Profile.class} would fail before it started: the mapped type now declares
 * {@code address} as a {@code @DBRef}, and the documents this migration exists to fix hold a String there. So it works
 * in {@link Document}s, where the old shape is just a value.</p>
 *
 * <p>It is idempotent — only profiles whose {@code address} is still a String are touched — so a partial run is safe
 * to repeat, which matters because there is no transaction to make the two writes atomic.</p>
 */
@ChangeUnit(id = "profile-address-to-document", order = "001", author = "hc-patient")
public class AddressAsDocumentMigration {

    private static final Logger LOG = LoggerFactory.getLogger(AddressAsDocumentMigration.class);

    private static final String PROFILE = "profile";
    private static final String ADDRESS = "address";

    private final MongoTemplate mongoTemplate;

    public AddressAsDocumentMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        // $type 'string' is what makes this idempotent: a profile already carrying a DBRef does not match, so a rerun
        // after a partial failure picks up only what is left.
        Query stillAString = new Query(Criteria.where(ADDRESS).type(2));
        List<Document> profiles = mongoTemplate.find(stillAString, Document.class, PROFILE);

        if (profiles.isEmpty()) {
            LOG.debug("No free-text addresses to migrate");
            return;
        }

        int migrated = 0;
        for (Document profile : profiles) {
            String text = profile.getString(ADDRESS);
            Object profileId = profile.get("_id");
            if (text == null || text.isBlank()) {
                // Nothing worth a document. Clear the field rather than leaving a String where a reference belongs,
                // or every read of this profile has to keep coping with the old shape forever.
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(profileId)), new Update().unset(ADDRESS), PROFILE);
                continue;
            }

            ObjectId addressId = new ObjectId();
            Document address = new Document("_id", addressId)
                .append("street_address", text)
                // The owner, so PatientScope can see it. Falls back to the profile's own id for records written
                // before patientId existed — the same fallback the rest of the service applies.
                .append("patient_id", profile.getString("patient_id") == null ? String.valueOf(profileId) : profile.getString("patient_id"))
                .append("created_date", LocalDate.now().toString());
            mongoTemplate.getCollection("address").insertOne(address);

            mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(profileId)),
                new Update().set(ADDRESS, new Document("$ref", "address").append("$id", addressId)),
                PROFILE
            );
            migrated++;
        }
        LOG.info("Migrated {} free-text profile addresses into address documents", migrated);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Rolling back would mean deleting the address documents this created and putting the strings back, and by then
     * a patient may have edited one through the portal — the rollback would silently discard that. Nothing in this
     * service deletes patient data, and a migration is not the place to make the first exception. Recovering from a
     * bad run means restoring the database, which is the honest answer rather than a convenient one.</p>
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("profile-address-to-document is not rolled back automatically — see the class comment");
    }
}
