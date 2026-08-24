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
 *
 * <h2>Reviewed 2026-08-24</h2>
 *
 * <p>Three rows changed after review — carer gained {@code DIAGNOSIS} reads, therapist gained {@code MEDICATION}
 * reads, paramedic gained {@code MEDICATION} writes. Each is explained at the row itself. All three widened, which
 * is the expected direction: this table refuses when unsure, so its errors accumulate on the side that a clinician
 * notices and complains about within minutes rather than the side nobody ever sees.</p>
 *
 * <h2>Two limits this model has, recorded rather than hidden</h2>
 *
 * <p><strong>There is no notion of a doctor's specialty.</strong> A dermatologist and a psychiatrist are the same
 * row, and both hold the whole record. This is the largest simplification here, and closing it would make every
 * {@code DIAGNOSIS} check specialty-dependent.</p>
 *
 * <p><strong>{@code CHEMIST} and {@code TECHNICIAN} were identical rows until 2026-08-24</strong>, when it was
 * confirmed that a chemist here is a <em>dispensing</em> chemist rather than a laboratory one. They now differ, and
 * the row's own comment explains what that cost while it was wrong.</p>
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
        // they read the plan they are carrying out and the allergies that would make them stop.
        //
        // THEY ALSO READ THE DIAGNOSIS, decided 2026-08-24, reversing this table's original position. The old
        // reasoning was that a carer does not need the name of a condition to follow the plan for it. That is true
        // of following a plan and false of recognising an emergency: a carer alone with a patient at home who does
        // not know they are diabetic, epileptic or living with dementia may not understand what they are watching
        // happen. The disclosure is real — carers are often agency staff — and it was judged the lesser risk,
        // because the carer is also the person present when it matters.
        //
        // Reads only. A carer never writes a diagnosis, and the write set below is unchanged.
        grant(
            AuthoritiesConstants.CARER,
            EnumSet.of(
                ClinicalDomain.DIAGNOSIS,
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
        // happened, what was measured, and — since 2026-08-24 — what they gave.
        //
        // MEDICATION writes were missing and it was a real gap, not a deliberate narrowing. A paramedic attending
        // an emergency may well administer something, and a record that cannot hold it is worse than no record:
        // the next clinician reads an incomplete medication history and prescribes against it.
        grant(
            AuthoritiesConstants.PARAMEDIC,
            EnumSet.allOf(ClinicalDomain.class),
            EnumSet.of(ClinicalDomain.MEDICATION, ClinicalDomain.OBSERVATION, ClinicalDomain.ENCOUNTER)
        );

        // A pharmacist: the medication record, and the conditions that bear on it. Writes medications and nothing
        // else.
        grant(
            AuthoritiesConstants.PHARMACIST,
            EnumSet.of(ClinicalDomain.DIAGNOSIS, ClinicalDomain.MEDICATION, ClinicalDomain.IDENTITY),
            EnumSet.of(ClinicalDomain.MEDICATION)
        );

        // A therapist: works to the plan and reports against it.
        //
        // MEDICATION reads added 2026-08-24. Their absence was an omission rather than a decision — no clinical
        // reasoning for it was ever recorded — and it left a physiotherapist working without facts that change what
        // is safe to do. Anticoagulants change what a fall costs; beta blockers blunt the heart-rate response that
        // exercise tolerance is judged by, so a therapist reading a pulse without knowing about them is reading it
        // wrong. Read only: a therapist does not prescribe, and the write set is unchanged.
        grant(
            AuthoritiesConstants.THERAPIST,
            EnumSet.of(
                ClinicalDomain.DIAGNOSIS,
                ClinicalDomain.MEDICATION,
                ClinicalDomain.CARE_PLAN,
                ClinicalDomain.OBSERVATION,
                ClinicalDomain.ENCOUNTER,
                ClinicalDomain.IDENTITY
            ),
            EnumSet.of(ClinicalDomain.CARE_PLAN, ClinicalDomain.ENCOUNTER)
        );

        // A technician runs and records tests. They produce observations; they do not interpret them, and they have
        // no reason to read what the patient is being treated for.
        grant(
            AuthoritiesConstants.TECHNICIAN,
            EnumSet.of(ClinicalDomain.OBSERVATION, ClinicalDomain.IDENTITY),
            EnumSet.of(ClinicalDomain.OBSERVATION)
        );

        // A chemist is a DISPENSING chemist — a community medicine outlet — and not a laboratory role. Confirmed
        // 2026-08-24, and it made this row wrong in the direction nobody notices.
        //
        // It had been written as a copy of the technician's on the assumption that "chemist" meant laboratory
        // chemist, which left somebody who hands medicines to patients unable to see the medication record or the
        // allergies. {@link ClinicalDomain#MEDICATION} groups allergies WITH medications for exactly this reason,
        // in its own words: anyone who may dispense must be able to see what would harm the patient. A dispensing
        // chemist could not, and nothing failed — they simply saw an empty list.
        //
        // Writes as well as reads, on the paramedic's reasoning: a record that cannot hold what was dispensed
        // leaves the next clinician prescribing against an incomplete history.
        //
        // DIAGNOSIS reads too, confirmed 2026-08-24. The same reason the pharmacist has them: dispensing safely
        // means knowing what the medicine is FOR, and a dispenser who cannot see the indication cannot catch a
        // medicine that is wrong for the condition — which is a large part of what the role is for.
        //
        // OBSERVATION is kept. It was granted for the wrong reason, but community outlets do take blood pressures,
        // and removing it would lock somebody out of work they really do — the error this table's own note says is
        // discovered in minutes and complained about, rather than the silent kind.
        //
        // NOTE THE INCONSISTENCY THIS LEAVES, because it is the next thing to be wrong here: the chemist now holds
        // everything the pharmacist holds AND observations, so the less qualified role is the wider one. The fault
        // is almost certainly on the pharmacist's side rather than this one — renal function and weight change
        // dosing, and a pharmacist can currently see neither. Raised in docs/scope-of-practice-review.md and
        // deliberately not fixed here, because widening the pharmacist is a clinical decision and not a symmetry
        // argument.
        grant(
            AuthoritiesConstants.CHEMIST,
            EnumSet.of(ClinicalDomain.DIAGNOSIS, ClinicalDomain.MEDICATION, ClinicalDomain.OBSERVATION, ClinicalDomain.IDENTITY),
            EnumSet.of(ClinicalDomain.MEDICATION, ClinicalDomain.OBSERVATION)
        );
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
