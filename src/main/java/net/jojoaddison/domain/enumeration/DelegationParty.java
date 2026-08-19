package net.jojoaddison.domain.enumeration;

/**
 * Which side ended a delegation.
 *
 * <p>It decides who gets told. A patient withdrawing access is a private act that the angel is informed of as a
 * courtesy; an angel stepping down leaves the patient without one, and they have to be told so they can nominate
 * somebody else. A patient who silently has no care angel is exactly the person the arrangement exists to protect.</p>
 */
public enum DelegationParty {
    PATIENT,
    ANGEL,
}
