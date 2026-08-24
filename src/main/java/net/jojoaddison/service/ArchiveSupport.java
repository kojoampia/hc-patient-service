package net.jojoaddison.service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import net.jojoaddison.domain.Archivable;

/**
 * Retiring a patient record, once, for every entity that can be retired.
 *
 * <p>{@code ClinicalCaseService} had these two methods inline from 2026-08-22. They are here unchanged in behaviour
 * so that the other nine clinical entities get exactly the same semantics rather than nine approximations of
 * them — including the two that are easy to get wrong and are the reason this is shared at all.</p>
 *
 * <h2>Idempotent by refusal, not by silence</h2>
 *
 * <p>Archiving something already archived is a mistake somewhere — two clinicians working the same queue, or a
 * double-submitted button — and answering 200 to it would overwrite the first archiver's name and reason with the
 * second's, quietly rewriting who retired the record and why. It refuses instead.</p>
 *
 * <h2>The caller is stamped, never accepted</h2>
 *
 * <p>{@code archivedById} comes from the resolved caller and never from a payload. A value a client can choose is a
 * claim rather than a record, and this one answers "who retired this patient's data".</p>
 */
public final class ArchiveSupport {

    private ArchiveSupport() {}

    /**
     * @param entityName the caller's {@code ENTITY_NAME}, so the error names the entity the client asked about.
     * @param subject a human-readable noun for the message ("clinical case", "medication").
     */
    public static <T extends Archivable> T archive(
        Optional<T> found,
        String id,
        String professionalId,
        String reason,
        String entityName,
        String subject,
        Function<T, T> save
    ) {
        T record = found.orElseThrow(() -> new DomainStateException("No such " + subject, entityName, "idnotfound"));
        if (record.isArchived()) {
            throw new DomainStateException(
                "This " + subject + " was already archived on " + record.getArchivedAt(),
                entityName,
                "alreadyarchived"
            );
        }
        record.setArchivedAt(Instant.now());
        record.setArchivedById(professionalId);
        record.setArchiveReason(reason);
        return save.apply(record);
    }

    /** The way back. Without it, archiving is a delete with extra steps and the mistake it invites is unrecoverable. */
    public static <T extends Archivable> T unarchive(Optional<T> found, String id, String entityName, String subject, Function<T, T> save) {
        T record = found.orElseThrow(() -> new DomainStateException("No such " + subject, entityName, "idnotfound"));
        if (!record.isArchived()) {
            throw new DomainStateException("This " + subject + " is not archived", entityName, "notarchived");
        }
        record.setArchivedAt(null);
        record.setArchivedById(null);
        record.setArchiveReason(null);
        return save.apply(record);
    }
}
