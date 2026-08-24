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
    void theBlanketProfessionalRoleGrantsNothingAndIsNotClinical(ClinicalDomain domain) {
        // ROLE_PROFESSIONAL had a row here granting everything, and was removed with the authority on 2026-08-24.
        // Asserted by its literal string rather than a constant, because the constant is gone and this test's job is
        // to notice if it comes back: a token minted before the cutover still carries it, and it must now be exactly
        // as meaningless as any other unrecognised role rather than quietly retaining the run of the record.
        assertThat(ScopeOfPractice.canRead(of("ROLE_PROFESSIONAL"), domain)).isFalse();
        assertThat(ScopeOfPractice.canWrite(of("ROLE_PROFESSIONAL"), domain)).isFalse();
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
            assertThat(mayDiagnose).as("%s writing a diagnosis", authority).isEqualTo(authority.equals(AuthoritiesConstants.DOCTOR));
        }
    }

    @Test
    void theRowsChangedByTheClinicalReviewHoldTheirNewGrants() {
        // Reviewed 2026-08-24. Asserted explicitly rather than left to the table, because each of these was a
        // decision with a reason and a silent revert would look like a tidy-up.

        // A carer alone with a patient at home has to be able to recognise what they are watching happen.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.CARER), ClinicalDomain.DIAGNOSIS)).isTrue();
        // Reads only. A carer never asserts what is wrong with somebody.
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.CARER), ClinicalDomain.DIAGNOSIS)).isFalse();

        // Anticoagulants and beta blockers change what is safe to do and how a pulse should be read.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.THERAPIST), ClinicalDomain.MEDICATION)).isTrue();
        // A therapist does not prescribe.
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.THERAPIST), ClinicalDomain.MEDICATION)).isFalse();

        // A paramedic who gave something in an emergency has to be able to record it, or the next clinician
        // prescribes against an incomplete history.
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.PARAMEDIC), ClinicalDomain.MEDICATION)).isTrue();
    }

    @Test
    void aDispensingChemistIsNotALaboratoryRole() {
        // Confirmed 2026-08-24: "chemist" here means a DISPENSING chemist, a community medicine outlet. The row had
        // been a copy of the technician's, which left somebody who hands medicines to patients unable to see the
        // medication record or the allergies -- and nothing failed, they simply saw an empty list.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.CHEMIST), ClinicalDomain.MEDICATION)).isTrue();
        assertThat(ScopeOfPractice.canWrite(of(AuthoritiesConstants.CHEMIST), ClinicalDomain.MEDICATION)).isTrue();

        // A technician does not dispense, and did not change.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.TECHNICIAN), ClinicalDomain.MEDICATION)).isFalse();

        // Where the chemist stops short of the pharmacist. A pharmacist reads the diagnosis because dispensing a
        // prescription safely means knowing what it is for; a dispensing chemist works a narrower counter. This is
        // the row's remaining open question, pinned so that changing it is a decision rather than a drift.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.CHEMIST), ClinicalDomain.DIAGNOSIS)).isFalse();
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.PHARMACIST), ClinicalDomain.DIAGNOSIS)).isTrue();
    }

    @Test
    void aLabRoleCannotReadWhatThePatientIsBeingTreatedFor() {
        // A technician runs a test. Being able to read the diagnosis alongside it is a disclosure nobody would
        // notice, which is the direction this table is meant to fail in. Chemist used to be asserted here too, and
        // is not any more -- see aDispensingChemistIsNotALaboratoryRole for why it was never a lab role.
        assertThat(ScopeOfPractice.canRead(of(AuthoritiesConstants.TECHNICIAN), ClinicalDomain.DIAGNOSIS)).isFalse();
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
            AuthoritiesConstants.PHARMACIST,
            // Added by the 2026-08-24 review: a therapist works the patient's body and must know what is in it,
            // and a dispensing chemist hands over the medicines this domain exists to make safe.
            AuthoritiesConstants.THERAPIST,
            AuthoritiesConstants.CHEMIST
        )) {
            assertThat(ScopeOfPractice.canRead(of(authority), ClinicalDomain.MEDICATION)).as("%s reading allergies", authority).isTrue();
        }
    }

    @Test
    void isClinicalRecognisesTheDisciplinesAndNothingElse() {
        assertThat(ScopeOfPractice.isClinical(of(AuthoritiesConstants.NURSE))).isTrue();
        // The removed blanket role. It reaches this service on any token minted before 2026-08-24 and must no longer
        // make its holder clinical -- which is what decides cross-patient access in PatientScope.isUnrestricted().
        assertThat(ScopeOfPractice.isClinical(of("ROLE_PROFESSIONAL"))).isFalse();
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
            .containsExactlyInAnyOrderElementsOf(AuthoritiesConstants.CLINICAL)
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
