package net.jojoaddison.service.dto;

/**
 * Step 3 — the patient's baseline readings, each becoming a {@code Stat}.
 *
 * <p>Height, weight and blood pressure are required; heart rate and blood sugar are not, because a patient filling
 * this in at home may simply not have the means to measure them, and a required field they cannot answer is a wall
 * rather than a question.</p>
 *
 * <p>No {@code flag} is derived from any of it. Judging a reading against a reference band is a clinical act, and
 * nothing here is a clinician.</p>
 */
public record OnboardingBaselineDTO(
    Double heightCm,
    Double weightKg,
    Double systolic,
    Double diastolic,
    Double heartRateBpm,
    Double bloodSugarMmolL
) {}
