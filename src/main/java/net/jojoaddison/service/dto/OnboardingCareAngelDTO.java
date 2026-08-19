package net.jojoaddison.service.dto;

/**
 * Step 2 — the care angel, and optionally a standby nominee.
 *
 * <p>The step completes on nomination. It does not wait for the angel to accept: a patient's access to their own
 * record must never depend on another person's inbox, and a mistyped address would otherwise lock them out of their
 * own medical history indefinitely.</p>
 *
 * @param firstName the angel's first name, which feeds the login the gateway derives.
 * @param lastName the angel's last name, likewise.
 * @param email the angel's email — load-bearing, since it is what a delegation is matched on.
 * @param standby an optional dormant nominee for the case the patient is later unable to nominate anyone.
 * @param advanceConsent the patient's authorisation for a clinician to activate the standby. Required if one is given,
 *                       and stored, because it is the only evidence the patient ever agreed to it.
 */
public record OnboardingCareAngelDTO(
    String firstName,
    String lastName,
    String fullName,
    String phone,
    String email,
    String contacts,
    StandbyNomineeDTO standby,
    Boolean advanceConsent
) {
    /** A nominee who is recorded and told nothing, until two clinicians and the nominee themselves say otherwise. */
    public record StandbyNomineeDTO(String firstName, String lastName, String fullName, String phone, String email) {}
}
