package net.jojoaddison.domain.enumeration;

/**
 * Where a patient is in the onboarding journey.
 *
 * <p>There is deliberately no {@code NOT_STARTED}. "Not started" is the absence of a {@link net.jojoaddison.domain.Profile}
 * altogether, which is how the guard already detects it, and adding a value for it would create a second way to say the
 * same thing.</p>
 *
 * <p>A <strong>null</strong> value means {@code COMPLETE}, not "not started". Every profile written before onboarding
 * existed reads null — production records, the development seed, the quality demo dataset — and treating those as
 * unfinished would drag every existing patient back through a wizard they never needed, and would show the quality
 * stack an onboarding form instead of the demo data it exists to demonstrate.</p>
 */
public enum OnboardingStatus {
    IN_PROGRESS,
    COMPLETE,
}
