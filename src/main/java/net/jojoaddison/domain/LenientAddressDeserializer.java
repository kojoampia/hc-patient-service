package net.jojoaddison.domain;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads an {@link Address} that may still be the free-text string it used to be.
 *
 * <p>{@code Profile.address} became a document when care onboarding needed a structured one. Stored documents are
 * handled by {@code AddressAsDocumentMigration}, but JSON is not: seed files, fixtures and any client written against
 * the old contract still send a plain string, and Jackson would refuse the whole payload rather than the one field.</p>
 *
 * <p>That refusal is worse than it sounds in the one place it matters most. {@code DevelopmentDataInitializer} catches
 * a failed read and loads <strong>nothing at all</strong>, so a single stale address in the quality stack's demo
 * document would empty the entire seeded dataset — leaving exactly the blank dashboard over a full database that
 * seeding exists to prevent.</p>
 *
 * <p>A string is read as a {@code streetAddress}, which is the same interpretation the migration applies to stored
 * data, so both paths agree about what "5 Ankobra River Street" means. Anything else is deserialized normally.</p>
 */
public class LenientAddressDeserializer extends ValueDeserializer<Address> {

    @Override
    public Address deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String text = parser.getString();
            return text == null || text.isBlank() ? null : new Address().streetAddress(text);
        }
        return context.readValue(parser, Address.class);
    }
}
