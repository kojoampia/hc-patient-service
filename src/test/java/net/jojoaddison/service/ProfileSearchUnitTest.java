package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The escaping the profile search's safety rests on.
 *
 * <p>Asserted as a property rather than as an expected string: what matters is not which characters get a backslash
 * but that the result matches its own input and nothing else. A test pinned to the exact output would have to be
 * rewritten every time a character is added to the set, and would stop saying anything about why.</p>
 */
class ProfileSearchUnitTest {

    /** Compiles the escaped term the way MongoDB would, and asks what it matches. */
    private static Pattern compiled(String term) {
        return Pattern.compile(ProfileSearch.escape(term), Pattern.CASE_INSENSITIVE);
    }

    @Test
    void anOrdinaryNameIsUnchangedInEffect() {
        assertThat(compiled("Ampia").matcher("Kojo Ampia-Addison").find()).isTrue();
        assertThat(compiled("ampia").matcher("Kojo Ampia-Addison").find()).isTrue();
        assertThat(compiled("Mensah").matcher("Kojo Ampia-Addison").find()).isFalse();
    }

    @Test
    void aWildcardMatchesOnlyItself() {
        // The one that matters. Unescaped, `.*` matches every profile in the database — an authorization boundary
        // stepped around by a query language rather than by a missing check, and a plausible thing for somebody to
        // type into a search box rather than an attack.
        assertThat(compiled(".*").matcher("Kojo Ampia-Addison").find()).isFalse();
        assertThat(compiled(".*").matcher("literally .* here").find()).isTrue();
    }

    @Test
    void aPhoneNumberWithBracketsIsAValidSearchRatherThanASyntaxError() {
        // Unescaped, this is an unbalanced group and reaches Mongo as a syntax error rather than as a search. No
        // malice needed: it is how people write phone numbers.
        assertThat(compiled("(024) 555").matcher("call (024) 555 0199").find()).isTrue();
    }

    @Test
    void aCatastrophicPatternIsInert() {
        // Unescaped, (a+)+$ against a long non-matching string backtracks exponentially: one request, every profile
        // scanned, the connection held open throughout.
        String evil = "(a+)+$";
        long start = System.nanoTime();
        boolean matched = compiled(evil).matcher("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab").find();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(matched).isFalse();
        assertThat(elapsedMillis).as("escaped pattern should match literally, not backtrack").isLessThan(1_000);
        assertThat(compiled(evil).matcher("a field containing (a+)+$ verbatim").find()).isTrue();
    }

    @Test
    void aBackslashDoesNotEscapeTheEscaping() {
        // Pattern.quote would wrap in \Q…\E, which a term containing \E closes early — putting the rest of the input
        // back into the pattern. Escaping character by character has no such edge.
        assertThat(compiled("\\E.*").matcher("Kojo").find()).isFalse();
        assertThat(compiled("a\\b").matcher("a\\b").find()).isTrue();
    }

    @Test
    void anchorsDoNotEscapeTheSubstringMatch() {
        assertThat(compiled("^Kojo").matcher("Kojo Ampia-Addison").find()).isFalse();
        assertThat(compiled("Addison$").matcher("Kojo Ampia-Addison").find()).isFalse();
    }

    @Test
    void anEmptyTermEscapesToNothing() {
        // The resource never calls it with a blank, but a helper that threw here would turn a harmless request into
        // a 500 the first time that changed.
        assertThat(ProfileSearch.escape("")).isEmpty();
    }
}
