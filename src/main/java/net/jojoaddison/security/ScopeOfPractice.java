package net.jojoaddison.security;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which clinical discipline may read and write which kind of patient data.
 *
 * <h2>Read this before changing it</h2>
 *
 * <p>This table is a <strong>starting position, not a clinical ruling</strong>. It was written from the shape of the
 * data rather than from anybody's scope of practice, and it is deliberately one readable table in one file so that
 * correcting it is a two-line change reviewed by somebody qualified to make it. Every entry below is a judgement
 * that can be wrong in two directions, and they are not symmetric: a nurse wrongly locked out of vitals is an
 * irritation discovered in minutes, and a technician wrongly able to read diagnoses is a disclosure nobody notices.
 * When unsure, this table refuses.</p>
 *
 * <h2>What it is not</h2>
 *
 * <p>It is not the scoping model. {@link PatientScope} still decides <em>whose</em> records a caller may touch, and
 * that question is answered first and separately. This decides <em>what kind</em> of data a caller may touch once
 * they are past it. A discipline that may write medications still writes them only for the patient in scope.</p>
 *
 * <p>{@code ROLE_ADMIN} is absent on purpose. An administrator is not a clinician and holds no scope of practice;
 * they are unrestricted for operational reasons and are handled before this table is consulted.</p>
 */
public final class ScopeOfPractice {

    /** What one discipline may do. Reads are a superset of writes in every row, and that is checked by a test. */
    private record Practice(Set<ClinicalDomain> reads, Set<ClinicalDomain> writes) {}

    private static final Map<String, Practice> BY_AUTHORITY = new LinkedHashMap<>();

    private static void grant(String authority, Set<ClinicalDomain> reads, Set<ClinicalDomain> writes) {
        BY_AUTHORITY.put(authority, new Practice(reads, writes));
    }

    static {
        // ROLE_PROFESSIONAL was the first row here — the generic clinical authority, granted everything because that
        // is what it had always meant. It was removed on 2026-08-24 along with the authority itself: nothing outside
        // this service ever issued or checked it, and while it existed every check that named it was a check no
        // clinician from hc-professional could pass. The rows below are the whole table now, so `isClinical` means
        // "holds one of the eight disciplines" and nothing else.

        // A doctor: the whole record, including the parts that assert something about the patient.
        grant(AuthoritiesConstants.DOCTOR, EnumSet.allOf(ClinicalDomain.class), EnumSet.allOf(ClinicalDomain.class));

        // A nurse: reads everything, and writes everything except the diagnosis. Administering and observing are
        // nursing acts; asserting what is wrong with the patient is not.
        grant(
            AuthoritiesConstants.NURSE,
            EnumSet.allOf(ClinicalDomain.class),
            EnumSet.of(ClinicalDomain.MEDICATION, ClinicalDomain.OBSERVATION, ClinicalDomain.CARE_PLAN, ClinicalDomain.ENCOUNTER)
        );

        // A carer, who is with the patient rather than treating them. They record what they see and what they did;
        // they read the plan they are carrying out and the allergies that would make them stop. Not the diagnosis:
        // a carer does not need to know the name of the condition to follow the plan for it, and this is the row
        // most likely to be wrong — see the note at the top.
        grant(
            AuthoritiesConstants.CARER,
            EnumSet.of(
                ClinicalDomain.MEDICATION,
                ClinicalDomain.OBSERVATION,
                ClinicalDomain.CARE_PLAN,
                ClinicalDomain.ENCOUNTER,
                ClinicalDomain.IDENTITY
            ),
            EnumSet.of(ClinicalDomain.OBSERVATION, ClinicalDomain.CARE_PLAN, ClinicalDomain.ENCOUNTER)
        );

        // A paramedic, who arrives knowing nothing and needs the dangerous facts immediately. Reads the diagnosis
        // because arriving at an unconscious patient without it is the case this role exists for; writes what
        // happened and what was measured.
        grant(
            AuthoritiesConstants.PARAMEDIC,
            EnumSet.allOf(ClinicalDomain.class),
            EnumSet.of(ClinicalDomain.OBSERVATION, ClinicalDomain.ENCOUNTER)
        );

        // A pharmacist: the medication record, and the conditions that bear on it. Writes medications and nothing
        // else.
        grant(
            AuthoritiesConstants.PHARMACIST,
            EnumSet.of(ClinicalDomain.DIAGNOSIS, ClinicalDomain.MEDICATION, ClinicalDomain.IDENTITY),
            EnumSet.of(ClinicalDomain.MEDICATION)
        );

        // A therapist: works to the plan and reports against it.
        grant(
            AuthoritiesConstants.THERAPIST,
            EnumSet.of(
                ClinicalDomain.DIAGNOSIS,
                ClinicalDomain.CARE_PLAN,
                ClinicalDomain.OBSERVATION,
                ClinicalDomain.ENCOUNTER,
                ClinicalDomain.IDENTITY
            ),
            EnumSet.of(ClinicalDomain.CARE_PLAN, ClinicalDomain.ENCOUNTER)
        );

        // A chemist and a technician run and record tests. They produce observations; they do not interpret them,
        // and they have no reason to read what the patient is being treated for.
        Set<ClinicalDomain> labReads = EnumSet.of(ClinicalDomain.OBSERVATION, ClinicalDomain.IDENTITY);
        Set<ClinicalDomain> labWrites = EnumSet.of(ClinicalDomain.OBSERVATION);
        grant(AuthoritiesConstants.CHEMIST, labReads, labWrites);
        grant(AuthoritiesConstants.TECHNICIAN, labReads, labWrites);
    }

    private ScopeOfPractice() {}

    /** Every authority this table knows about, in declaration order. */
    public static Set<String> knownAuthorities() {
        return BY_AUTHORITY.keySet();
    }

    /**
     * Whether any of the caller's authorities allows reading this kind of data.
     *
     * <p>The union across the caller's authorities, not the intersection: somebody who is both a nurse and a
     * pharmacist can do either job, and the alternative — the narrowest of their roles winning — would mean that
     * being given a second qualification took capability away.</p>
     */
    public static boolean canRead(Set<String> authorities, ClinicalDomain domain) {
        return authorities.stream().map(BY_AUTHORITY::get).anyMatch(practice -> practice != null && practice.reads().contains(domain));
    }

    /** Whether any of the caller's authorities allows writing this kind of data. */
    public static boolean canWrite(Set<String> authorities, ClinicalDomain domain) {
        return authorities.stream().map(BY_AUTHORITY::get).anyMatch(practice -> practice != null && practice.writes().contains(domain));
    }

    /** Whether the caller holds any clinical discipline at all — the replacement for "is a professional". */
    public static boolean isClinical(Set<String> authorities) {
        return authorities.stream().anyMatch(BY_AUTHORITY::containsKey);
    }
}
