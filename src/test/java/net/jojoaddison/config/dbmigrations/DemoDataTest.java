package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for the reading helpers behind {@link DemoDataInitializer}.
 *
 * <p>These cover the two places the demo file and the domain disagree — one {@code name} string against a first and
 * last name, and dates written sometimes as a date and sometimes as an instant — and the rule that a malformed field
 * yields nothing rather than an exception, because a typo in a hand-maintained demo file must not stop a developer's
 * application from starting.</p>
 */
class DemoDataTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode node(String json) {
        return MAPPER.readTree(json);
    }

    @ParameterizedTest(name = "\"{0}\" splits into \"{1}\" and \"{2}\"")
    @CsvSource(
        {
            "Dr. Ama Mensah, Ama, Mensah",
            "Kojo Ampia-Addison, Kojo, Ampia-Addison",
            "Kwabena Adda Frimpong, Kwabena Adda, Frimpong",
            "Prof. Nii Adjei Osae, Nii Adjei, Osae",
            "Ama, Ama,",
            "Dr. Mensah, Mensah,",
        }
    )
    void splitsADisplayNameIntoFirstAndLast(String displayName, String firstName, String lastName) {
        assertThat(DemoData.splitName(displayName)).containsExactly(firstName, lastName);
    }

    @Test
    void splitsNothingWhenThereIsNoName() {
        assertThat(DemoData.splitName(null)).containsExactly(null, null);
        assertThat(DemoData.splitName("   ")).containsExactly(null, null);
    }

    @Test
    void takesInitialsFromEachPartOfTheName() {
        assertThat(DemoData.initials("Dr. Ama Mensah")).isEqualTo("AM");
        assertThat(DemoData.initials("Kwabena Adda Frimpong")).isEqualTo("KF");
        assertThat(DemoData.initials("Ama")).isEqualTo("A");
        assertThat(DemoData.initials(null)).isNull();
    }

    @Test
    void readsADateWrittenEitherWay() {
        // The demo file writes dateOfBirth as a date and occurredAt as an instant, and both land in a LocalDate field.
        assertThat(DemoData.date(node("{\"at\":\"1976-04-19\"}"), "at")).isEqualTo(LocalDate.of(1976, 4, 19));
        assertThat(DemoData.date(node("{\"at\":\"2022-05-21T05:43:00Z\"}"), "at")).isEqualTo(LocalDate.of(2022, 5, 21));
    }

    @Test
    void readsAnInstant() {
        assertThat(DemoData.instant(node("{\"at\":\"2026-07-20T08:00:00Z\"}"), "at")).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
    }

    @Test
    void yieldsNothingRatherThanThrowingOnBadInput() {
        JsonNode broken = node("{\"at\":\"not-a-date\",\"nulled\":null,\"ids\":\"not-an-array\"}");

        assertThat(DemoData.date(broken, "at")).isNull();
        assertThat(DemoData.instant(broken, "at")).isNull();
        assertThat(DemoData.text(broken, "nulled")).isNull();
        assertThat(DemoData.text(broken, "absent")).isNull();
        assertThat(DemoData.date(broken, "absent")).isNull();
        assertThat(DemoData.stringSet(broken, "ids")).isEmpty();
        assertThat(DemoData.stringSet(broken, "absent")).isEmpty();
        assertThat(DemoData.array(broken, "absent")).isEmpty();
        assertThat(DemoData.bool(broken, "absent")).isFalse();
    }

    @Test
    void readsAStringArrayInTheOrderItWasWritten() {
        assertThat(DemoData.stringSet(node("{\"ids\":[\"b\",\"a\",\"c\"]}"), "ids")).containsExactly("b", "a", "c");
    }

    @Test
    void readsABoolean() {
        assertThat(DemoData.bool(node("{\"isChild\":true}"), "isChild")).isTrue();
        assertThat(DemoData.bool(node("{\"isChild\":false}"), "isChild")).isFalse();
    }
}
