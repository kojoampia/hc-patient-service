package net.jojoaddison.domain;

import java.time.Instant;

/**
 * A patient record that is retired rather than deleted.
 *
 * <h2>Why this exists as an interface</h2>
 *
 * <p>Patient data is never deleted: sixteen resources require {@code ROLE_ADMIN} for {@code DELETE}, and archiving is
 * the clinician's replacement for the delete they do not have. {@code ClinicalCase} had it from 2026-08-22 and
 * nothing else did, so fifteen resources named a rule with no way to obey it.</p>
 *
 * <p>Implemented rather than copied because the alternative was the same forty lines in ten documents, where the
 * tenth drifts from the first and nobody notices until a record archives without a reason. The behaviour lives once,
 * in {@link net.jojoaddison.service.ArchiveSupport}.</p>
 *
 * <h2>A nullable instant, not a boolean</h2>
 *
 * <p>The question asked about an archived record afterwards is <em>who</em> and <em>why</em>, and a boolean records
 * that it happened while losing both. It is also what makes the existing data correct without a migration: every
 * document written before these fields has no {@code archived_at} key at all, and in MongoDB a null match also
 * matches a missing field, so they all read as live. Queries must therefore use {@code IsNull} and never a boolean
 * test — {@code findByArchivedAtIsNull}, not {@code findByArchivedFalse}.</p>
 */
public interface Archivable {
    Instant getArchivedAt();

    void setArchivedAt(Instant archivedAt);

    String getArchivedById();

    void setArchivedById(String archivedById);

    String getArchiveReason();

    void setArchiveReason(String archiveReason);

    /** True once {@link #getArchivedAt()} is set. The only definition of archived there is. */
    default boolean isArchived() {
        return getArchivedAt() != null;
    }
}
