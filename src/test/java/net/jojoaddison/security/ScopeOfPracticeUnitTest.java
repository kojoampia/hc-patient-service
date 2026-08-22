package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The scope-of-practice table.
 *
 * <p>These are mostly invariants rather than a restatement of the table. A test that asserted "a pharmacist may
 * write MEDICATION" says the same thing the table says, in more words, and would be edited in lockstep with it —
 * proving only that somebody changed both. What is worth pinning is the structure: that a role can never write what
 * it cannot read, that an unknown authority grants nothing, and the handful of entries whose absence would be a
 * disclosure rather than an inconvenience.</p>
 */
class ScopeOfPracticeUnitTest {

    private static Set<String> of(String... authorities) {
        return Set.of(authorities);
    }

    @ParameterizedTest
    @EnumSource(ClinicalDomain.class)
    void nobodyCanWriteWhatTheyCannotRead(ClinicalDomain domain) {
        // The invariant that keeps the table coherent. A role able to write a medication record but not read it
        // could overwrite what it cannot see, which is worse than either permission alone.
        for (String authority : ScopeOfPractice.knownAuthorities()) {
            Set<String> caller = of(authority);
            if (ScopeOfPractice.canWrite(caller, domain)) {
                assertThat(ScopeOfPractice.canRead(caller, domain)).as("%s may write %s but not read it", authority, domain).isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ClinicalDomain.class)
    void anUnknownAuthorityGrantsNothing(ClinicalDomain domain) {
        // ROLE_USER, ROLE_PATIENT, ROLE_ANGEL and anything invented later. The table is an allowlist; a role it has
        // never heard of must not fall through to permitted.
        assertThat(ScopeOfPractice.canRead(of("ROLE_USER", "ROLE_PATIENT", "ROLE_SOMETHING_NEW"), domain)).isFalse();
        assertThat(ScopeOfPractice.canWrite(of("ROLE_USER", "ROLE_PATIENT", "ROLE_SOMETHING_NEW"), domain)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ClinicalDomain.class)
    void theBlanketProfessionalRoleKeepsEverythingItHad(ClinicalDomain domain) {
        // Thirty existing checks in this service gate on ROLE_PROFESSIONAL and mean "all clinical data". Narrowing
        // it here would change all of them at once, silently. Narrowing it is a migration, not a default.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.PROFESSIONAL), domain)).isTrue();
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.PROFESSIONAL), domain)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ClinicalDomain.class)
    void aDoctorHoldsTheWholeRecord(ClinicalDomain domain) {
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.DOCTOR), domain)).isTrue();
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.DOCTOR), domain)).isTrue();
    }

    @Test
    void twoQualificationsAddUpRatherThanCancelOut() {
        // The union, not the intersection. Somebody who is both a nurse and a pharmacist can do either job, and the
        // alternative would mean that being given a second qualification took capability away.
        Set<String> both = of(AuthoritiesConstants.NURSE, AuthoritiesConstants.PHARMACIST);

        assertThat(ScopeOfPractice.canWrite(both, ClinicalDomain.MEDICATION)).isTrue();
        assertThat(ScopeOfPractice.canWrite(both, ClinicalDomain.OBSERVATION)).isTrue();
        // Still not the diagnosis: neither of them holds it, and adding them does not conjure it.
        assertThat(ScopeOfPractice.canWrite(both, ClinicalDomain.DIAGNOSIS)).isFalse();
    }

    @Test
    void onlyTheDiagnosingRolesMayWriteADiagnosis() {
        // The entry whose absence would be a clinical error rather than an inconvenience: a record asserting what is
        // wrong with a patient, written by somebody not qualified to assert it.
        for (String authority : ScopeOfPractice.knownAuthorities()) {
            boolean mayDiagnose = ScopeOfPractice.canWrite(of(authority), ClinicalDomain.DIAGNOSIS);
            assertThat(mayDiagnose)
                .as("%s writing a diagnosis", authority)
                .isEqualTo(authority.equals(AuthoritiesConstants.DOCTOR) || authority.equals(AuthoritiesConstants.PROFESSIONAL));
        }
    }

    @Test
    void aLabRoleCannotReadWhatThePatientIsBeingTreatedFor() {
        // A technician runs a test. Being able to read the diagnosis alongside it is a disclosure nobody would
        // notice, which is the direction this table is meant to fail in.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.TECHNICIAN), ClinicalDomain.DIAGNOSIS)).isFalse();
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.CHEMIST), ClinicalDomain.DIAGNOSIS)).isFalse();
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.TECHNICIAN), ClinicalDomain.OBSERVATION)).isTrue();
    }

    @Test
    void everyoneWhoTreatsThePatientCanSeeWhatWouldHarmThem() {
        // Allergies live in MEDICATION precisely so this is one question. Any role that administers or dispenses
        // must be able to read it; being unable to is the failure that kills somebody.
        for (String authority : Set.of(
            AuthoritiesConstants.DOCTOR,
            AuthoritiesConstants.NURSE,
            AuthoritiesConstants.CARER,
            AuthoritiesConstants.PARAMEDIC,
            AuthoritiesConstants.PHARMACIST
        )) {
            assertThat(ScopeOfPractice.canRead(of(authority), ClinicalDomain.MEDICATION)).as("%s reading allergies", authority).isTrue();
        }
    }

    @Test
    void isClinicalRecognisesTheDisciplinesAndNothingElse() {
        assertThat(ScopeOfPractice.isClinical(of(AuthoritiesConstants.NURSE))).isTrue();
        assertThat(ScopeOfPractice.isClinical(of(AuthoritiesConstants.PROFESSIONAL))).isTrue();
        assertThat(ScopeOfPractice.isClinical(of("ROLE_USER", "ROLE_PATIENT"))).isFalse();
        assertThat(ScopeOfPractice.isClinical(of(AuthoritiesConstants.ADMIN))).isFalse();
        assertThat(ScopeOfPractice.isClinical(Set.of())).isFalse();
    }

    @Test
    void everyDisciplineTheProfessionalGatewayIssuesIsKnownHere() {
        // hc-professional's gateway mints these eight and no ROLE_PROFESSIONAL. A token from there reaches this
        // service through the shared signing key, so any name missing from this table is a clinician who signs in
        // successfully and is served empty lists — the failure this whole change exists to fix.
        assertThat(ScopeOfPractice.knownAuthorities())
            .contains(
                AuthoritiesConstants.DOCTOR,
                AuthoritiesConstants.NURSE,
                AuthoritiesConstants.CARER,
                AuthoritiesConstants.PARAMEDIC,
                AuthoritiesConstants.PHARMACIST,
                AuthoritiesConstants.THERAPIST,
                AuthoritiesConstants.CHEMIST,
                AuthoritiesConstants.TECHNICIAN
            );
    }
}
