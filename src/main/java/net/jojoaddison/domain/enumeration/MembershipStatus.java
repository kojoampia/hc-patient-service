package net.jojoaddison.domain.enumeration;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a patient's subscription to a care plan stands.
 *
 * <h2>The five values, and why five</h2>
 *
 * <p>A patient choosing a tier writes {@code PENDING}; an administrator's approval sets {@code ACTIVE}. The other
 * three are the ways a live membership stops being one. It was specified as {@code {PENDING, VERIFIED}} and that was
 * rejected on evidence rather than on taste: the shipped i18n bundles in {@code web} and {@code mobile} already
 * promise exactly these five, in every language each app ships, and both clients pick the patient's held plan with
 * {@code status?.toUpperCase() === 'ACTIVE'} — so a two-value vocabulary would have made that selector match nothing
 * and fall through to the first membership in the list, silently, on the patient's own screen.</p>
 *
 * <p><b>{@code VERIFIED} is deliberately not a member.</b> Ruled 2026-09-08: an administrator's approval sets
 * {@code ACTIVE} directly, and the record of <em>who</em> approved and <em>when</em> is an audit fact kept beside the
 * membership rather than a state it passes through. A status that meant "approved but not yet in force" would be a
 * value neither client renders as held, which is a limbo a patient would read as their plan having quietly lapsed.
 * Where that audit record lives is settled with the inbound {@code patient-events-plan} consumer, which is blocked on
 * hc-admin; nothing here should anticipate its shape.</p>
 *
 * <h2>⚠ This overturns the ruling {@link IdentificationType} records, and the difference is the point</h2>
 *
 * <p>{@code IdentificationType}'s javadoc argues the <em>opposite</em> for free-text {@code Profile.cardType}:
 * <em>"A value already stored that is not in this list must still read. Binding the field to an enum makes an
 * unrecognised legacy value throw while deserialising, which turns a patient's profile screen into an error instead
 * of showing a slightly untidy string."</em> Both decisions are correct and they are not in tension, but the reason
 * has to be written down or the next reader will conclude one of them is a mistake and 'fix' it.</p>
 *
 * <p>That reasoning protects values that are <b>unenumerable and passed through</b>. {@code cardType} was typed into
 * a free-text box by people, so the set of things already stored is open — nobody can list it, and four constants
 * were removed from that enum on 2026-08-31, which means a patient really does hold a {@code PASSPORT} that must
 * still render. There is nothing to migrate it <em>to</em>.</p>
 *
 * <p>{@code Membership.status} is the other case on both counts. The set is <b>closed and known</b>: this service
 * wrote every value in it, the clients only ever send {@code PENDING}, and the one stored spelling that differs is
 * the quality seed's lower-case {@code "active"}. And it is <b>migrated rather than tolerated</b> —
 * {@link net.jojoaddison.config.dbmigrations.MembershipStatusAsEnumMigration} rewrites the stored casings onto these
 * constants before anything reads them through the mapped type, so there is no legacy value left for strictness to
 * trip over. Tolerance buys nothing when you can enumerate the values and rewrite them; it is the whole answer when
 * you can do neither.</p>
 *
 * <p>The practical difference is what a strict binding would cost. For {@code cardType} it is a patient's profile
 * screen turning into an error. Here it is nothing, because after the migration there is no unrecognised value to
 * bind — and if one ever appeared it would mean somebody had written to this collection outside the service, which is
 * a thing worth failing loudly about rather than rendering.</p>
 */
public enum MembershipStatus {
    /** Chosen by the patient and awaiting a decision. What {@code POST /api/memberships} records, whoever asks. */
    PENDING,
    /** Approved and in force. The only value either client renders as the patient's held plan. */
    ACTIVE,
    /** Ended by the subscriber. */
    CANCELLED,
    /** Ran past its renewal date without being renewed. */
    EXPIRED,
    /** Live but not currently honoured — a billing hold rather than an ending. */
    SUSPENDED;

    /**
     * Matches loosely, so that a stored or supplied spelling resolves to its constant.
     *
     * <p>Used by the migration to map what is already in the database, and available to anything that has to accept a
     * value it did not write. Unlike {@link IdentificationType#canonicalise(String)} this <b>does not pass unknown
     * values through</b>: there is nowhere for one to go once the field is typed, and quietly keeping it would
     * reintroduce exactly the untyped field this replaces.</p>
     *
     * @param raw the value as written, in any casing, or null.
     * @return the matching constant, or empty when there is none.
     */
    public static Optional<MembershipStatus> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (MembershipStatus status : values()) {
            if (status.name().equals(key)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
