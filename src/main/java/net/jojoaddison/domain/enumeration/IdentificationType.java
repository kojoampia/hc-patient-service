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
 * <h2>The Ghana Card, and nothing else — ruled 2026-08-31</h2>
 *
 * <p>This was five constants and marked <em>proposed, not settled</em>. It is now one, and settled: <b>BridgeCare
 * accepts the Ghana Card as identification and does not accept passports, voter IDs, NHIS cards or driving
 * licences.</b></p>
 *
 * <p>The reasoning is worth keeping, because a single-constant enum looks like an oversight to anybody who meets it
 * cold. The national ID is mandatory and universal, so <b>accepting alternatives buys nothing and invites ambiguity
 * about what was actually verified</b> — two patients holding "verified identification" would mean two different
 * things, and neither the record nor the person reading it could tell which.</p>
 *
 * <p>A single value is <em>not</em> a reason to collapse this back to a bare {@code String}. The type is what stops
 * the next document type being added by somebody typing it into a form.</p>
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
 * recognises and passes through what it does not.</p>
 *
 * <p><b>Narrowing the list to one made that tolerance more load-bearing, not less</b> — which is the opposite of
 * how it reads. Four constants were removed on 2026-08-31, so a patient who completed onboarding before then with
 * a passport has {@code PASSPORT} stored on their profile. Binding the field to this enum, or rejecting values not
 * in it, would turn that patient's own profile screen into an error. The value is no longer <em>accepted</em>; it
 * is still <em>readable</em>, and those are different questions. Quality holds no such values (3 profiles, all
 * {@code card_type} null) and production has never been readable from here, so this is written as a guarantee
 * rather than as an assumption that none exist.</p>
 *
 * <p>Tightening to strict rejection therefore remains a later change, and one that would need a migration for any
 * stored value that is no longer accepted — not merely a client that has caught up.</p>
 */
public enum IdentificationType {
    GHANA_CARD("Ghana Card");

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
        // "NATIONAL_ID" and "GHANACARD" are what people write for the same document. Recognising them here is
        // what keeps the stored value single-valued; it is not a second accepted document type.
        if ("NATIONAL_ID".equals(key) || "GHANACARD".equals(key) || "GHANA_NATIONAL_ID".equals(key)) {
            return Optional.of(GHANA_CARD);
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
