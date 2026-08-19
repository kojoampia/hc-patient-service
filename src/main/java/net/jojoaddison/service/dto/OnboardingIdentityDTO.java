package net.jojoaddison.service.dto;

import java.time.LocalDate;

/**
 * Step 1 — who the patient is, and the payload that bootstraps their record.
 *
 * <p>Deliberately not a {@code Profile}. The domain document carries {@code email}, {@code patientId} and {@code id},
 * and a caller who could set those could create a profile for somebody else, or attach themselves to an existing
 * patient. The server takes the email from the token and mints the rest.</p>
 */
public record OnboardingIdentityDTO(
    String firstName,
    String middleNames,
    String lastName,
    LocalDate birthDate,
    String sex,
    String mobilePhone,
    String phoneNumber,
    OnboardingAddressDTO address
) {}
