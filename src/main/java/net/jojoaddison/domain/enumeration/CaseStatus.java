package net.jojoaddison.domain.enumeration;

/**
 * Lifecycle of a clinical case: raised, assessed, under treatment, then closed.
 */
public enum CaseStatus {
    URGENT,
    OPEN,
    TREATMENT,
    CLOSED,
}
