package net.jojoaddison.service.dto;

import java.util.List;

/**
 * Step 4 — what the patient is currently living with.
 *
 * <p>Each repeatable group carries an explicit "none" flag rather than relying on an empty list. "I have no allergies"
 * and "I have not answered yet" are different clinical statements, and a resumable wizard cannot tell them apart from
 * the absence of rows. The distinction is recorded in the step marker, not by writing a placeholder document.</p>
 *
 * <p>The flags are boxed {@code Boolean} rather than {@code boolean}, for two reasons that happen to agree. Jackson 3
 * refuses to map an absent property onto a primitive at all ({@code FAIL_ON_NULL_FOR_PRIMITIVES}), so a client that
 * omitted one would get an unhelpful "failed to read request". And null genuinely means something here — it is
 * "unanswered", which is precisely the state this record exists to distinguish from "none".</p>
 */
public record OnboardingCurrentStateDTO(
    String bloodGroup,
    List<ConditionEntryDTO> conditions,
    Boolean noConditions,
    List<AllergyEntryDTO> allergies,
    Boolean noAllergies,
    List<MedicationEntryDTO> medications,
    Boolean noMedications
) {
    public record ConditionEntryDTO(String name, String description) {}

    /** {@code category} and {@code severity} are the AllergyCategory / AllergySeverity names. */
    public record AllergyEntryDTO(String name, String category, String severity, String reaction) {}

    /** {@code status} is a MedicationStatus name; {@code startedOn} an ISO date. */
    public record MedicationEntryDTO(String name, String dosage, String prescription, String status, String startedOn) {}
}
