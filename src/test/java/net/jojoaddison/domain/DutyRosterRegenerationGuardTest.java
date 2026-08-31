package net.jojoaddison.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails loudly if regenerating {@code DutyRoster} drops {@code subscribedProfessionalIds}.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>{@code patient.jdl} is the model of record and renders both repos' {@code .jhipster/*.json}. It cannot
 * express this field: <b>JDL has no list-of-scalars type.</b> So the field is maintained by hand, and
 * regenerating the entity deletes it — silently, because a generator overwriting a file is not an error.</p>
 *
 * <p>The alternative was a {@code @DBRef} many-to-many to {@link Professional}, rejected because it would be the
 * third relationship in a domain that holds every other cross-entity reference as a bare String id.</p>
 *
 * <p>Until now the only guard was a comment in the entity saying "add this field back by hand" — <b>and a comment
 * is read by somebody who already suspects a problem.</b> Whoever regenerates the entity is, by definition, not
 * that person: they run the generator, the build passes, the tests pass, and the field is gone. The data goes
 * with it on the next write, because Spring Data maps what the class declares and nothing else.</p>
 *
 * <p>That failure has a precedent in this workspace worth naming: a warning about a wrong deploy script outlived
 * the script it warned about by nineteen days, in {@code gateway/CLAUDE.md}. Prose does not fail a build.</p>
 *
 * <h2>If this test fails</h2>
 *
 * <p>You have almost certainly just regenerated the entity. Re-add the field, its {@code @Field} mapping and its
 * three accessors from git history — do not delete this test. If the domain ever grows a <em>second</em>
 * list-of-scalars field, that is the signal to revisit the JDL decision rather than repeat this guard.</p>
 */
class DutyRosterRegenerationGuardTest {

    private static final String FIELD = "subscribedProfessionalIds";

    @Test
    @DisplayName("DutyRoster still declares subscribedProfessionalIds as a Set<String>")
    void theFieldSurvives() throws Exception {
        Field field = DutyRoster.class.getDeclaredField(FIELD);

        assertThat(field.getType()).as("%s must stay a Set — a regenerated entity would not have it at all", FIELD).isEqualTo(Set.class);

        // Set<String>, not Set<Professional>: the whole point of the decision this guards is that the reference
        // stays a bare String id rather than becoming a third @DBRef relationship.
        ParameterizedType generic = (ParameterizedType) field.getGenericType();
        assertThat(generic.getActualTypeArguments()[0])
            .as("%s must hold String ids, not a @DBRef to Professional", FIELD)
            .isEqualTo(String.class);
    }

    @Test
    @DisplayName("it keeps its explicit Mongo field mapping")
    void theMongoMappingSurvives() throws Exception {
        Field field = DutyRoster.class.getDeclaredField(FIELD);
        org.springframework.data.mongodb.core.mapping.Field mapping = field.getAnnotation(
            org.springframework.data.mongodb.core.mapping.Field.class
        );

        // Without the explicit name, Spring Data would map it to "subscribedProfessionalIds" and every document
        // already written under subscribed_professional_ids would read back empty — the data would still be in
        // Mongo, and the application would behave as though every roster had no subscribers.
        assertThat(mapping).as("%s must keep its @Field mapping", FIELD).isNotNull();
        assertThat(mapping.value()).isEqualTo("subscribed_professional_ids");
    }

    @Test
    @DisplayName("its accessors survive, including the null-coalescing setter")
    void theAccessorsSurvive() throws Exception {
        assertThat(DutyRoster.class.getMethod("get" + capitalised())).isNotNull();
        assertThat(DutyRoster.class.getMethod("set" + capitalised(), Set.class)).isNotNull();
        assertThat(DutyRoster.class.getMethod(FIELD, Set.class)).isNotNull();

        // The setter coalesces null to an empty set. A regenerated setter would assign null straight through,
        // and every read site here iterates the set without a null check.
        DutyRoster roster = new DutyRoster();
        roster.setSubscribedProfessionalIds(null);
        assertThat(roster.getSubscribedProfessionalIds()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("the entity still carries the comment explaining why the field is hand-maintained")
    void theExplanationSurvives() throws Exception {
        // Belt and braces, and deliberately so. The assertions above would still pass if somebody re-added the
        // field while deleting the paragraph that says why it cannot be generated — and then the next person to
        // regenerate has no warning at all, only this test failing with no explanation of what to do.
        Path source = Path.of("src/main/java/net/jojoaddison/domain/DutyRoster.java");
        assertThat(source).exists();
        String text = Files.readString(source);

        assertThat(text)
            .as("the javadoc must keep saying the field is not in patient.jdl and must be re-added by hand")
            .contains("regenerated");
        assertThat(text).contains(FIELD);
    }

    private static String capitalised() {
        return Character.toUpperCase(FIELD.charAt(0)) + FIELD.substring(1);
    }
}
