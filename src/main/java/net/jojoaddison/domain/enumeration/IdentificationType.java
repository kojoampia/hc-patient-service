package net.jojoaddison.domain.enumeration;

import java.util.Locale;
import java.util.Optional;

/**
 * The forms of identification BridgeCare recognises, and the canonical spelling of each.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code Profile.cardType} is free text that onboarding writes straight through, so "Ghana Card", "ghana card"
 * and "GhanaCard" were three different values that nothing could group — and
 * {@code portal/profile/profile.component.html} renders the stored string <em>raw</em> to the patient, so whatever
 * somebody typed is what they and their care team then read back. The web form is a plain
 * {@code <input required>}; only its own spec ever used an enum-shaped value ({@code GHANA_CARD}).</p>
 *
 * <h2>This list is a starting position, not a legal ruling</h2>
 *
 * <p><b>Which documents BridgeCare accepts as identification is a product and compliance question, and nobody has
 * answered it.</b> These five are the ones in common use in Ghana and are proposed, not settled — the same posture
 * {@code ScopeOfPractice} takes about its table, and for the same reason: a wrong entry here should be a one-line
 * change, and pretending the question is closed is how it stops being asked. Adding or removing a constant is
 * exactly that one line.</p>
 *
 * <h2>Deliberately tolerant on read, canonical on write</h2>
 *
 * <p>{@code Profile.cardType} stays a {@code String} on the document rather than becoming this type. Two reasons,
 * and the second is the one that matters:</p>
 *
 * <p>A value already stored that is not in this list must still <em>read</em>. Binding the field to an enum makes
 * an unrecognised legacy value throw while deserialising, which turns a patient's profile screen into an error
 * instead of showing a slightly untidy string. That is the same trade the archiving work made with {@code IsNull}
 * rather than a boolean — tolerate what is already written, constrain what is written next.</p>
 *
 * <p>And <b>rejecting unrecognised input outright would break onboarding the moment this service deployed ahead of
 * the clients.</b> The web form still posts free text; a strict service would answer 400 to every new patient
 * completing step 5, with the clients none the wiser. That is precisely the cross-repo ordering failure the
 * {@code Stat} pagination work already cost this subsystem, so {@link #canonicalise(String)} normalises what it
 * recognises and passes through what it does not. Tightening to strict rejection is a later change, safe only once
 * both clients ship a constrained control.</p>
 */
public enum IdentificationType {
    GHANA_CARD("Ghana Card"),
    PASSPORT("Passport"),
    VOTER_ID("Voter ID"),
    NHIS("NHIS card"),
    DRIVERS_LICENCE("Driver's licence");

    private final String label;

    IdentificationType(String label) {
        this.label = label;
    }

    /**
     * How this should be shown to a patient.
     *
     * <p>The portal renders the stored value directly, so without a label a patient who picked Ghana Card would be
     * shown {@code GHANA_CARD}. Clients are expected to translate; this is the fallback and the canonical English.</p>
     */
    public String label() {
        return label;
    }

    /**
     * Matches loosely — case, padding, hyphens, spaces and the label spelling all resolve to the same constant.
     *
     * <p>Loose on purpose: the values this has to recognise were typed by people into a free-text box. "ghana card",
     * "Ghana-Card" and "GHANA_CARD" are the same document.</p>
     */
    public static Optional<IdentificationType> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('\'', '_').replaceAll("\\s+", "_");
        for (IdentificationType type : values()) {
            if (type.name().equals(key) || type.label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "_").equals(key)) {
                return Optional.of(type);
            }
        }
        // "DRIVERS_LICENSE" — the American spelling, which somebody will type.
        if ("DRIVERS_LICENSE".equals(key) || "DRIVING_LICENCE".equals(key) || "DRIVING_LICENSE".equals(key)) {
            return Optional.of(DRIVERS_LICENCE);
        }
        return Optional.empty();
    }

    /**
     * The canonical spelling of {@code raw} if it is recognised, otherwise {@code raw} trimmed and unchanged.
     *
     * <p><b>Never rejects.</b> See the class javadoc: a service that refused unknown values would break onboarding
     * for every patient the moment it deployed ahead of a client still posting free text.</p>
     */
    public static String canonicalise(String raw) {
        if (raw == null) {
            return null;
        }
        return from(raw).map(Enum::name).orElse(raw.trim());
    }
}
