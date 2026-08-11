package net.jojoaddison.domain.enumeration;

/**
 * Where a shift sits relative to now: still to come, being worked, or over.
 *
 * <p>Deliberately not {@link ScheduleStatus}, which is the lifecycle of a patient appointment
 * (confirmed, pending, attended, cancelled) and answers a different question. A shift is not
 * attended or cancelled; it starts and it ends.</p>
 */
public enum ShiftStatus {
    ACTIVE,
    UPCOMING,
    COMPLETED,
}
