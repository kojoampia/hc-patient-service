package net.jojoaddison.domain.enumeration;

/**
 * Who put an entry on the activity timeline.
 *
 * <p>{@code ANGEL} is a care angel acting for the patient. It is not a shade of {@code PATIENT}: collapsing the two
 * would show a patient their angel's entry as though they had written it themselves, and with delegation that stops
 * being a corner case — it is the expected path whenever a patient is incapacitated, which is precisely when getting
 * attribution right matters most.</p>
 */
public enum ActivitySource {
    PATIENT,
    PROFESSIONAL,
    ANGEL,
    SYSTEM,
}
