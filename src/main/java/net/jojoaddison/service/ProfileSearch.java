package net.jojoaddison.service;

/**
 * Turns what somebody typed into something safe to put in a MongoDB {@code $regex}.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>The search query interpolates the term directly into a regular expression, which is the only way to get
 * case-insensitive substring matching across six fields out of MongoDB without a text index. That makes the term
 * <em>code</em> rather than data, and two things follow if it is passed through untouched:</p>
 *
 * <ul>
 *   <li>A search for {@code .*} matches every patient in the system. Not an attack — a plausible thing to type, and
 *       an authorization boundary that a query language quietly steps around.</li>
 *   <li>A search for {@code (a+)+$} hands the database a pattern whose backtracking is exponential in the length of
 *       the text it is matched against. One request, every profile scanned, the connection held open throughout.</li>
 * </ul>
 *
 * <p>Neither needs a malicious user. A phone number typed with brackets — {@code (024) 555 0199} — is an unbalanced
 * group as far as a regex engine is concerned, and reaches Mongo as a syntax error rather than a search.</p>
 *
 * <h2>Why not {@code Pattern.quote}</h2>
 *
 * <p>{@code Pattern.quote} wraps its argument in {@code \Q…\E}. MongoDB's engine understands that sequence, so it
 * would mostly work — until the term itself contains {@code \E}, which closes the quoting early and puts the rest of
 * the input back into the pattern. Escaping each character is longer and has no such edge.</p>
 */
public final class ProfileSearch {

    /**
     * Every character with a meaning in a PCRE pattern.
     *
     * <p>Includes {@code #} and whitespace because MongoDB's {@code $regex} accepts an extended-mode flag, and a
     * pattern built for one set of options should not change meaning under another.</p>
     */
    private static final String META = "\\^$.|?*+()[]{}#";

    private ProfileSearch() {}

    /**
     * Escapes a search term so that every character in it matches itself.
     *
     * @param term what the user typed.
     * @return the same text, safe to interpolate into a {@code $regex}.
     */
    public static String escape(String term) {
        StringBuilder escaped = new StringBuilder(term.length() * 2);
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (META.indexOf(c) >= 0 || Character.isWhitespace(c)) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
