package net.jojoaddison.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The two properties that matter here pull against each other, and the second is the one that is easy to lose.
 *
 * <p><b>Canonical:</b> the values a patient types must collapse to one spelling, or nothing can group them and the
 * portal shows each patient whatever they happened to write.</p>
 *
 * <p><b>Never rejecting:</b> this service must stay deployable ahead of the clients. The web onboarding form is
 * still a plain free-text input, so a strict service would answer 400 to every patient finishing step 5 — with a
 * 200-shaped journey right up to the last step and nothing in either client to explain it. That is the same
 * cross-repo ordering failure the {@code Stat} pagination work already cost this subsystem.</p>
 */
class IdentificationTypeTest {

    @ParameterizedTest
    @DisplayName("the spellings a person actually types all resolve to Ghana Card")
    @ValueSource(strings = { "GHANA_CARD", "ghana card", "Ghana Card", "  Ghana-Card  ", "GHANA CARD", "ghana_card" })
    void recognisesTheWaysPeopleWriteGhanaCard(String typed) {
        assertThat(IdentificationType.from(typed)).contains(IdentificationType.GHANA_CARD);
        assertThat(IdentificationType.canonicalise(typed)).isEqualTo("GHANA_CARD");
    }

    @ParameterizedTest
    @DisplayName("the ways people write the national ID all resolve to Ghana Card")
    @ValueSource(strings = { "NATIONAL_ID", "GhanaCard", "ghana_national_id", "national id" })
    void recognisesTheNationalIdSpellings(String typed) {
        // Recognising these keeps the STORED value single-valued. It is not a second accepted document type.
        assertThat(IdentificationType.from(typed)).contains(IdentificationType.GHANA_CARD);
        assertThat(IdentificationType.canonicalise(typed)).isEqualTo("GHANA_CARD");
    }

    @ParameterizedTest
    @DisplayName("a type that is no longer ACCEPTED is still READABLE")
    @ValueSource(strings = { "PASSPORT", "VOTER_ID", "NHIS", "DRIVERS_LICENCE" })
    void removedTypesStillRead(String storedBeforeTheRuling) {
        // These four were accepted until 2026-08-31. A patient who onboarded with a passport has PASSPORT sitting
        // on their profile right now. Binding the field to this enum, or rejecting what is not in it, would turn
        // that patient's own profile screen into an error.
        //
        // No longer ACCEPTED and still READABLE are different questions, and this is the test that keeps them
        // apart when somebody later "finishes" the narrowing.
        assertThat(IdentificationType.from(storedBeforeTheRuling)).isEmpty();
        assertThat(IdentificationType.canonicalise(storedBeforeTheRuling)).isEqualTo(storedBeforeTheRuling);
    }

    @Test
    @DisplayName("an unrecognised value passes through instead of being rejected")
    void anUnrecognisedValuePassesThrough() {
        // THE TEST THAT KEEPS THIS DEPLOYABLE IN ANY ORDER. The web form is free text today. If canonicalise threw
        // or returned null here, deploying this service before the client would break onboarding for every new
        // patient at the final step — with a 200 all the way to it.
        assertThat(IdentificationType.from("Student ID")).isEmpty();
        assertThat(IdentificationType.canonicalise("Student ID")).isEqualTo("Student ID");
    }

    @Test
    @DisplayName("an unrecognised value is still trimmed, so padding alone never makes two values")
    void anUnrecognisedValueIsStillTrimmed() {
        assertThat(IdentificationType.canonicalise("  Student ID  ")).isEqualTo("Student ID");
    }

    @Test
    @DisplayName("null and blank are left as they are rather than invented into a value")
    void nullAndBlankAreLeftAlone() {
        assertThat(IdentificationType.canonicalise(null)).isNull();
        assertThat(IdentificationType.from(null)).isEmpty();
        assertThat(IdentificationType.from("   ")).isEmpty();
        // Onboarding's own requireText is what rejects an empty answer. Doing it here too would put the same rule
        // in two places, and they would drift.
        assertThat(IdentificationType.canonicalise("   ")).isEmpty();
    }

    @Test
    @DisplayName("every constant has a label that is not just its name shouted")
    void everyConstantHasAHumanLabel() {
        // profile.component.html renders the stored value directly. Without labels a patient who picked Ghana Card
        // is shown GHANA_CARD on their own profile screen.
        for (IdentificationType type : IdentificationType.values()) {
            assertThat(type.label()).isNotBlank();
            assertThat(type.label()).isNotEqualTo(type.name());
        }
    }

    @Test
    @DisplayName("a label round-trips back to its own constant")
    void labelsRoundTrip() {
        // Clients may post the label rather than the constant — it is what the dropdown shows. If these did not
        // round-trip, picking from a dropdown would store an unrecognised value and look exactly like free text.
        for (IdentificationType type : IdentificationType.values()) {
            assertThat(IdentificationType.from(type.label())).as("label '%s' must resolve to %s", type.label(), type).contains(type);
        }
    }

    @Test
    @DisplayName("canonicalising a canonical value changes nothing")
    void isIdempotent() {
        for (IdentificationType type : IdentificationType.values()) {
            String once = IdentificationType.canonicalise(type.name());
            assertThat(IdentificationType.canonicalise(once)).isEqualTo(once);
        }
    }
}
