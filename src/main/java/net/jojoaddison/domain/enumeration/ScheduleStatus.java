package net.jojoaddison.domain.enumeration;

/**
 * Appointment lifecycle, used by Task when it carries a scheduled visit.
 */
public enum ScheduleStatus {
    CONFIRMED,
    PENDING,
    ATTENDED,
    CANCELLED,
}
