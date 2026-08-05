package net.jojoaddison.security;

import java.time.LocalDate;
import net.jojoaddison.config.Constants;

/**
 * Who touched a record, and when — decided here rather than accepted from the request body.
 *
 * <p>The entities in this service declare their own {@code createdBy}, {@code modifiedBy}, {@code createdDate} and
 * {@code modifiedDate} fields instead of extending {@link net.jojoaddison.domain.AbstractAuditingEntity}, which does
 * exist here, is correctly annotated, and is extended by nothing. Resources bind the domain object straight from the
 * request body with no DTO layer, so until 2026-08-05 every one of those fields was attacker-controlled: a caller
 * could attribute any record to any user and backdate it at will.</p>
 *
 * <p>That matters beyond tidiness. For a health record system the audit trail is the thing you reach for when
 * investigating who saw or changed what — and an audit trail the subject can rewrite answers that question with
 * whatever they chose to write in it.</p>
 *
 * <p>Deliberately not fixed by moving the entities onto {@code AbstractAuditingEntity}: that base class stores under
 * {@code created_by}/{@code last_modified_by}, which are different field names and a different Mongo shape from what
 * these collections already hold and what the dashboard's models expect. Stamping in the resource layer preserves the
 * documented no-DTO convention and needs no data migration.</p>
 *
 * <p>Note the dates are {@link LocalDate}, not an instant — a day's resolution, which is what the entities model. A
 * real audit trail wants a timestamp and an action, and that is a schema change rather than a security fix.</p>
 */
public final class AuditStamp {

    private AuditStamp() {}

    /**
     * The login recorded against a write.
     *
     * <p>Falls back to {@code system} when there is no authenticated principal — a Mongock migration or a Kafka
     * consumer, for instance. It never falls back to whatever the caller supplied.</p>
     *
     * @return the current user's login, or {@code system}.
     */
    public static String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM);
    }

    /**
     * The date recorded against a write.
     *
     * @return today, in the server's zone.
     */
    public static LocalDate today() {
        return LocalDate.now();
    }
}
