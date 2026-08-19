package net.jojoaddison.service.dto;

/** Where the patient lives, in the shape the Address document actually has. */
public record OnboardingAddressDTO(
    String digitalAddress,
    String streetAddress,
    String areaCode,
    String town,
    String city,
    String district,
    String state,
    String region,
    String country
) {}
