package net.jojoaddison.service.dto;

/**
 * Step 5 — identification.
 *
 * <p>Required, with no "none" option: onboarding cannot complete without both fields. The document upload the design
 * also wants is not here, because {@code PersonalDocument} has no file sub-resource yet and making a mandatory step
 * depend on a backend that does not exist is how it becomes an unpassable one.</p>
 */
public record OnboardingIdentificationDTO(String cardType, String cardNumber) {}
