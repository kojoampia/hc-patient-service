package net.jojoaddison.config.dbmigrations;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Reading helpers for the demo-data document consumed by {@link DemoDataInitializer}.
 *
 * <p>Every accessor tolerates a missing or null field and returns {@code null} (or an empty set) rather than throwing,
 * because the demo file is hand-maintained and a typo in it should cost one blank field in a development database, not
 * a failed application start.</p>
 */
final class DemoData {

    private DemoData() {}

    /**
     * Reads a text field.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the text, or {@code null} when the field is absent, null or not textual.
     */
    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /**
     * Reads a boolean field.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the value, or {@code false} when the field is absent or null.
     */
    static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean();
    }

    /**
     * Reads an ISO-8601 instant field, e.g. {@code 2026-07-20T08:00:00Z}.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the instant, or {@code null} when the field is absent, null or unparseable.
     */
    static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Reads a date field, accepting either an ISO date ({@code 1976-04-19}) or an ISO instant, which is truncated to
     * its UTC date. The demo file uses both spellings for what the domain stores as a {@code LocalDate}.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the date, or {@code null} when the field is absent, null or unparseable.
     */
    static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException notADate) {
            Instant at = instant(node, field);
            return at == null ? null : at.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    /**
     * Reads an array of strings into a set, preserving the file's order.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the values, or an empty set when the field is absent, null or not an array.
     */
    static Set<String> stringSet(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return new LinkedHashSet<>();
        }
        Set<String> values = new LinkedHashSet<>();
        array.forEach(element -> {
            if (!element.isNull()) {
                values.add(element.asString());
            }
        });
        return values;
    }

    /**
     * Iterates an array field, or nothing at all when it is absent.
     *
     * @param node the object to read from.
     * @param field the field name.
     * @return the array's elements, or an empty iterable.
     */
    static Iterable<JsonNode> array(JsonNode node, String field) {
        JsonNode array = node.get(field);
        return array == null || !array.isArray() ? Set.of() : array;
    }

    /**
     * Splits a display name into a first and last name.
     *
     * <p>The demo file carries one {@code name}/{@code patientName} string where the domain has {@code firstName} and
     * {@code lastName}. A leading honorific is dropped ({@code Dr. Ama Mensah} gives {@code Ama} / {@code Mensah}),
     * the last whitespace-separated token becomes the last name, and everything before it the first name — so
     * {@code Kwabena Adda Frimpong} gives {@code Kwabena Adda} / {@code Frimpong}. A single-token name becomes a first
     * name with no last name.</p>
     *
     * @param displayName the full name as written in the demo file.
     * @return a two-element array: first name, then last name. Either may be {@code null}.
     */
    static String[] splitName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new String[] { null, null };
        }
        String[] tokens = displayName.trim().split("\\s+");
        int from = tokens.length > 1 && HONORIFICS.contains(tokens[0].toLowerCase(java.util.Locale.ROOT)) ? 1 : 0;
        if (tokens.length - from <= 1) {
            return new String[] { tokens[tokens.length - 1], null };
        }
        String lastName = tokens[tokens.length - 1];
        String firstName = String.join(" ", java.util.Arrays.copyOfRange(tokens, from, tokens.length - 1));
        return new String[] { firstName, lastName };
    }

    private static final Set<String> HONORIFICS = new HashSet<>(
        Set.of("dr.", "dr", "mr.", "mr", "mrs.", "mrs", "ms.", "ms", "prof.", "prof")
    );

    /**
     * Builds initials from a display name — {@code Dr. Ama Mensah} gives {@code AM}.
     *
     * @param displayName the full name as written in the demo file.
     * @return the initials, or {@code null} when there is no name to take them from.
     */
    static String initials(String displayName) {
        String[] parts = splitName(displayName);
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.isEmpty() ? null : initials.toString();
    }
}
