package net.jojoaddison.domain.enumeration;

/**
 * Whether a prescription is still being taken. WITHHELD records a drug deliberately not given — the patient record has to be able to show \"Amoxicillin — not given, penicillin allergy\".
 */
public enum MedicationStatus {
    ACTIVE,
    COMPLETED,
    WITHHELD,
}
