package net.jojoaddison.domain.enumeration;

/**
 * Who took a reading.
 *
 * <p>The same distinction {@link ActivitySource} draws on the timeline, and for the same reason: a glucose reading the
 * patient took at home before breakfast and one a nurse took on a home visit are different kinds of record, and the
 * portal says "you" about the first. Without this, a self-measured reading either names nobody or borrows the name of
 * a clinician who was not there.</p>
 *
 * <p>{@code DEVICE} has no reader at all — a cuff or a meter that reports on its own. It is listed now because the
 * telemetry ingestion in Phase C will produce exactly that, and a reading with no {@code recordedById} needs to be
 * distinguishable from one whose recorder was simply never filled in.</p>
 */
public enum StatSource {
    PATIENT,
    PROFESSIONAL,
    DEVICE,
}
