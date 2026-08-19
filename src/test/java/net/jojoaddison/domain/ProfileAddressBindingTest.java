package net.jojoaddison.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * That a profile carrying the old free-text address still binds.
 *
 * <p>{@code Profile.address} became a document when care onboarding needed a structured one, but seed documents,
 * fixtures and anything else written before that carry a plain string. Jackson would otherwise refuse the whole
 * payload — and in the one place that matters most, {@code DevelopmentDataInitializer} catches the failure and loads
 * <em>nothing at all</em>, so a single stale address empties the entire seeded dataset. The quality stack's demo
 * document holds exactly such a string.</p>
 */
class ProfileAddressBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aFreeTextAddressIsReadAsAStreetAddress() {
        Profile profile = mapper.readValue("{\"firstName\":\"Ama\",\"address\":\"5 Ankobra River Street\"}", Profile.class);

        assertThat(profile.getAddress()).isNotNull();
        assertThat(profile.getAddress().getStreetAddress()).isEqualTo("5 Ankobra River Street");
    }

    @Test
    void aStructuredAddressStillBinds() {
        Profile profile = mapper.readValue("{\"address\":{\"streetAddress\":\"9 Ring Road\",\"town\":\"Accra\"}}", Profile.class);

        assertThat(profile.getAddress().getStreetAddress()).isEqualTo("9 Ring Road");
        assertThat(profile.getAddress().getTown()).isEqualTo("Accra");
    }

    @Test
    void anAbsentAddressIsStillAbsent() {
        assertThat(mapper.readValue("{\"firstName\":\"Ama\"}", Profile.class).getAddress()).isNull();
    }
}
