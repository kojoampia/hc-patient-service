package net.jojoaddison.security;

/**
 * The kinds of patient data a discipline can be given or refused, as opposed to the twenty-three collections they
 * are stored in.
 *
 * <p>Grouped rather than one constant per entity, because scope of practice is about <em>kinds of information</em>
 * and not about tables. A pharmacist's relationship to {@code Medication} and to {@code Allergy} is the same
 * relationship; splitting them would invite a matrix where the two disagree by accident.</p>
 *
 * <p>Adding an entity means deciding which of these it belongs to — a question with an answer — rather than adding a
 * row to a grid nobody can hold in their head.</p>
 */
public enum ClinicalDomain {
    /**
     * What is wrong with the patient: {@code ClinicalCase}, {@code Condition}, {@code Recommendation},
     * {@code Report}. The diagnostic record — the part of a chart that asserts something about the person.
     */
    DIAGNOSIS,

    /**
     * What the patient is given, and what they must not be: {@code Medication}, {@code Allergy}.
     *
     * <p>Allergy sits here rather than under {@link #DIAGNOSIS} because it is read at the moment of prescribing and
     * by the same people. A pharmacist who may dispense must be able to see what would harm the patient.</p>
     */
    MEDICATION,

    /**
     * What was measured: {@code Stat}. Vitals, taken by whoever is with the patient.
     */
    OBSERVATION,

    /**
     * What the patient is meant to do, and what is meant to happen to them: {@code CarePlanItem}, {@code Task}.
     */
    CARE_PLAN,

    /**
     * Where the patient is in the system: {@code Visitation}, {@code Emergency}, {@code ActivityLog}.
     *
     * <p>Emergency is here rather than under {@link #DIAGNOSIS} deliberately. Raising an alarm is not a diagnostic
     * act, and the discipline least likely to hold a diagnostic authority — a carer alone with a patient at
     * home — is the one most likely to need it.</p>
     */
    ENCOUNTER,

    /**
     * Who the patient is: {@code Profile}, {@code Address}, {@code PersonalDocument}, {@code PaymentOption},
     * {@code Membership}, {@code Metadata}.
     *
     * <p>Administrative rather than clinical, and deliberately not restricted by discipline — everyone who may reach
     * a patient at all needs to know whose record they are looking at.</p>
     */
    IDENTITY,
}
