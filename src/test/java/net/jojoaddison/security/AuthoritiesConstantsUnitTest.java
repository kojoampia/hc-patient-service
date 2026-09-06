package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The two spellings of "any clinician", and that they agree.
 *
 * <p>{@link AuthoritiesConstants#CLINICAL} is a {@code Set} for Java code and
 * {@link AuthoritiesConstants#CLINICAL_AUTHORITIES} is a pre-quoted string for {@code @PreAuthorize}, which takes a
 * compile-time constant and cannot read a {@code Set}. The duplication is deliberate and this class is the price of
 * it: without these assertions, adding a ninth discipline to one and not the other is a silent hole in the
 * twenty-four reference-data checks that use the string form — the annotations would still compile, still deploy,
 * and simply never admit the new role.</p>
 */
class AuthoritiesConstantsUnitTest {

    /** The string form, unpicked back into the set it claims to be. */
    private static Set<String> parsed() {
        return Arrays
            .stream(AuthoritiesConstants.CLINICAL_AUTHORITIES.split(","))
            .map(String::trim)
            .map(entry -> entry.replace("'", ""))
            .collect(Collectors.toSet());
    }

    @Test
    void theSetAndTheExpressionNameTheSameDisciplines() {
        assertThat(parsed()).containsExactlyInAnyOrderElementsOf(AuthoritiesConstants.CLINICAL);
    }

    @Test
    void theExpressionIsQuotedReadyForSpel() {
        // hasAnyAuthority(...) takes quoted literals, and the quotes live inside the constant so call sites do not
        // add their own. A missing quote here is not a compile error anywhere -- it is a SpEL parse failure at the
        // first request to reach one of twenty-four endpoints, in production, long after the build went green.
        for (String entry : AuthoritiesConstants.CLINICAL_AUTHORITIES.split(",")) {
            assertThat(entry.trim()).startsWith("'").endsWith("'");
        }
    }

    @Test
    void theDisciplinesAreSpelledAsHcProfessionalSpellsThem() {
        // Byte-identical to hc-professional's own AuthoritiesConstants. There is no shared artefact between the two
        // repositories to enforce it, so this list is the enforcement: a token from that stack carries these exact
        // strings, and a typo here is a clinician who signs in successfully and is served empty lists.
        assertThat(AuthoritiesConstants.CLINICAL)
            .containsExactlyInAnyOrder(
                "ROLE_DOCTOR",
                "ROLE_NURSE",
                "ROLE_CARER",
                "ROLE_PARAMEDIC",
                "ROLE_PHARMACIST",
                "ROLE_THERAPIST",
                "ROLE_CHEMIST",
                "ROLE_TECHNICIAN"
            );
    }

    @Test
    void theBlanketProfessionalRoleIsNotAmongThem() {
        // ROLE_PROFESSIONAL was removed on 2026-08-24. Reintroducing it here would restore the blanket role under a
        // new name and undo the point of the change: every one of these checks would pass for a role no other stack
        // in the platform issues.
        assertThat(AuthoritiesConstants.CLINICAL).doesNotContain("ROLE_PROFESSIONAL");
        assertThat(AuthoritiesConstants.CLINICAL_AUTHORITIES).doesNotContain("ROLE_PROFESSIONAL");
    }

    @Test
    void theCareAngelRoleIsNotAmongThem() {
        // THE ESTATE DECIDED ON 2026-09-06 THAT AN ANGEL IS NOT A CLINICAL DISCIPLINE (backlog item 3).
        //
        // ROLE_ANGEL and these eight are not the same kind of thing. A discipline is a standing capability; an angel's
        // authority is an ACTIVE CareDelegation over ONE patient, re-read per request so a revocation takes effect on
        // the next call rather than when a rememberMe token expires. PatientScope says it in one line: "ROLE_ANGEL
        // grants nothing. An ACTIVE CareDelegation grants everything."
        //
        // This test exists because the divergence it settles was first reported AS A DEFECT IN THIS REPO — "ROLE_ANGEL
        // is missing from the clinical set", of the same shape as the ROLE_DOCTOR omission genuinely fixed on
        // 2026-08-22. Adding it here would look like closing that gap and would in fact delete the boundary:
        // every angel in the estate would gain unrestricted cross-patient read, which is the one thing CareDelegation
        // exists to prevent. hc-professional narrows to these eight; this repo does not widen to nine.
        assertThat(AuthoritiesConstants.CLINICAL).doesNotContain(AuthoritiesConstants.ANGEL);
        assertThat(AuthoritiesConstants.CLINICAL_AUTHORITIES).doesNotContain(AuthoritiesConstants.ANGEL);
    }
}
