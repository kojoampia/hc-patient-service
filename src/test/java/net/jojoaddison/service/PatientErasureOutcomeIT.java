package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DeletionRequest;
import net.jojoaddison.domain.Metadata;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.domain.Report;
import net.jojoaddison.domain.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;

/**
 * That an erasure leaves nothing of the patient behind — asserted as an outcome, not as a list.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code PatientErasureService.PATIENT_SCOPED} names the collections the sweep clears, and until 2026-09-10 the
 * only thing checking it was a guard that scanned the domain package for {@code @Document} classes carrying a
 * {@code patient_id} field and asserted the two sets were equal. <b>The predicate that guard discovered by was the
 * property it was guarding.</b> Rename a class's field away from {@code patient_id} and it leaves the discovered set;
 * drop the class from {@code PATIENT_SCOPED} — which the guard's own failure message told you to do — and the two sets
 * agree again, eight tests pass, and the collection is never erased. Measured on {@code Stat}, not inferred. The
 * codebase already had one live instance of the same blindness: {@code PaymentOption} stores the patient id under
 * {@code user_id} and was missed for exactly that reason. See {@code docs/backlog.md} item 31.</p>
 *
 * <h2>The shape, and the one property that matters</h2>
 *
 * <p><b>This test never looks at a field name.</b> It enumerates every {@code @Document} class in the domain package,
 * writes one document per collection with <em>every</em> {@code String} property set to a sentinel that stands for the
 * subject — so whether a class calls it {@code patient_id}, {@code user_id} or {@code owner_id} is not a question this
 * test asks — runs the erasure, and then reads every collection in the database back looking for that sentinel
 * anywhere in any value. Nothing about that can be blinded by a rename, in the domain or in the erasure.</p>
 *
 * <p>The one thing it has to be told is which collections are <em>not</em> a patient's data. That is
 * {@link #SURVIVES_THE_ERASURE}, and it is a class-level declaration with a written reason rather than a field-name
 * convention: a new {@code @Document} is patient data until somebody says otherwise in writing, and a rename cannot
 * move a class in or out of it.</p>
 *
 * <h2>What it covers that the per-collection tests beside it do not</h2>
 *
 * <p>{@code PatientErasureServiceIT} keeps the tests about <em>decisions</em> — that the delegations this person held
 * over others are revoked, that a blank id is refused, that re-running is safe, that the counts name what was removed,
 * and that the plan-verification ledger goes rather than being kept as an audit record. This one covers the mechanical
 * question those cannot: that the sweep reaches every collection there is, including GridFS, which is erased by a
 * separate query on {@code metadata.patientId} — seeded here through {@link ReportFileService#store} rather than by
 * writing that key by hand, so a rename of the metadata key fails this test too.</p>
 *
 * <p>The one thing only this test says is that {@link DeletionRequest} <em>survives</em>: it is the single collection
 * carrying a {@code patient_id} that the erasure deliberately leaves alone, and until now nothing asserted that either
 * way.</p>
 */
@IntegrationTest
class PatientErasureOutcomeIT {

    /**
     * The person being erased. Every string in their fixture is this exact value, in every collection.
     *
     * <p>Deliberately unlike anything else in the suite: the survival check is a substring scan over every collection
     * in the database, and a shared Testcontainers Mongo means another class's fixture could otherwise answer it.</p>
     */
    private static final String SUBJECT = "erasure-outcome-subject";

    /** Somebody else, seeded identically, so that "nothing survives" cannot be satisfied by emptying the database. */
    private static final String NEIGHBOUR = "erasure-outcome-neighbour";

    /** GridFS's file collection, which is not a {@code @Document} class and so has to be named. */
    private static final String GRIDFS_FILES = "fs.files";

    /** A file that is a PDF by its first bytes, which is what {@link ReportFileService} decides the type from. */
    private static final byte[] A_PDF = "%PDF-1.4 a scanned lab slip".getBytes(StandardCharsets.UTF_8);

    /**
     * The collections a patient's erasure deliberately does not empty, each with the reason.
     *
     * <p>This is the whole of what the test is told, and it is told it per <em>class</em> rather than per field. A
     * {@code @Document} that is not named here must be gone after an erasure; adding one here is a positive claim that
     * the collection holds nobody's patient data, which is a much louder thing to write than deleting a line from a
     * list because a field was renamed.</p>
     */
    private static final Map<Class<?>, String> SURVIVES_THE_ERASURE = declaredSurvivors();

    private static Map<Class<?>, String> declaredSurvivors() {
        Map<Class<?>, String> declared = new LinkedHashMap<>();
        declared.put(
            DeletionRequest.class,
            "the evidence the erasure was asked for, authorised and carried out - argued in PatientErasureService's javadoc"
        );
        declared.put(Metadata.class, "service-level key/value data with no patient link at all");
        declared.put(Professional.class, "a clinician's own record, not a patient's");
        declared.put(
            Recommendation.class,
            "a catalogue of labels shared across every patient; its only patient-bearing field is the inverse @DBRef " +
            "side of the ClinicalCase relationship, which nothing in this service populates"
        );
        declared.put(Team.class, "staff reference data, like Professional");
        return Collections.unmodifiableMap(declared);
    }

    /** Every persisted document class, found by the annotation and by nothing else. */
    private static final List<Class<?>> DOCUMENT_CLASSES = everyDocumentClassInTheDomain();

    @Autowired
    private PatientErasureService patientErasureService;

    @Autowired
    private ReportFileService reportFileService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void removeWhateverThisTestSeeded() {
        for (Class<?> type : DOCUMENT_CLASSES) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(idFor(SUBJECT, type), idFor(NEIGHBOUR, type))), type);
        }
        reportFileService.deleteFor(reportOf(SUBJECT));
        reportFileService.deleteFor(reportOf(NEIGHBOUR));
    }

    /**
     * Sixty seconds rather than the suite's thirty: this writes two documents into every collection there is, erases,
     * and reads the whole database back three times. Measured at ~21 s on a loaded {@code jacserver}, most of it the
     * forty-six writes. A slower test was an accepted cost of asserting the outcome rather than the list.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void nothingKeyedToTheErasedPatientSurvivesInAnyCollection() throws Exception {
        seedOneDocumentInEveryCollection(SUBJECT);
        seedOneDocumentInEveryCollection(NEIGHBOUR);

        // Before erasing anything, prove the fixture reached every collection. Without this the assertion below is
        // satisfied just as well by a seed that silently wrote nothing, which is the shape of failure item 31 is about.
        assertThat(collectionsMentioning(SUBJECT))
            .as("the fixture must key one document to the subject in every @Document collection and in GridFS")
            .containsExactlyInAnyOrderElementsOf(everyCollectionSeeded());

        patientErasureService.erase(SUBJECT, SUBJECT);

        assertThat(collectionsMentioning(SUBJECT))
            .as(
                "An erasure must leave nothing of the patient anywhere. Any collection listed here still holds a " +
                "document keyed to the erased patient and is neither cleared by PatientErasureService nor declared " +
                "in SURVIVES_THE_ERASURE. Add it to PatientErasureService.PATIENT_SCOPED, or -- if it genuinely is " +
                "nobody's patient data -- declare it in SURVIVES_THE_ERASURE with the reason. Renaming the field it " +
                "is keyed by will not help: this test does not look at field names, which is why it exists."
            )
            .containsExactlyInAnyOrderElementsOf(declaredSurvivorCollections());

        assertThat(collectionsMentioning(NEIGHBOUR))
            .as("erasing one patient took a document belonging to somebody else with it")
            .containsExactlyInAnyOrderElementsOf(everyCollectionSeeded());
    }

    // --- fixtures -----------------------------------------------------------------------------------------------

    /**
     * One document per collection, with every {@code String} property set to the subject.
     *
     * <p>Setting them all is the point: the test cannot then be told which field carries the patient, so it cannot be
     * blinded by that field being called something else. It is written through {@code MongoTemplate}, so the mapping
     * decides the stored field names — a {@code @Field} rename in the domain changes what this writes, exactly as it
     * changes what the erasure has to find.</p>
     */
    private void seedOneDocumentInEveryCollection(String subject) throws Exception {
        for (Class<?> type : DOCUMENT_CLASSES) {
            Object document = type.getDeclaredConstructor().newInstance();
            for (java.lang.reflect.Field property : writableStringFieldsOf(type)) {
                property.set(document, subject);
            }
            // Last, so it is unique per subject per collection rather than the sentinel itself. It still carries the
            // sentinel, so a collection that keyed its patient by _id would be covered too.
            idFieldOf(type).set(document, idFor(subject, type));
            mongoTemplate.save(document);
        }

        // GridFS is erased by its own query rather than by the loop over PATIENT_SCOPED, so it needs its own document.
        // Stored through the production writer so that the metadata key it is found by comes from main code.
        reportFileService.store(reportOf(subject), new MockMultipartFile("file", "results.pdf", "application/pdf", A_PDF));
    }

    private static Report reportOf(String subject) {
        return new Report().id(idFor(subject, Report.class)).patientId(subject);
    }

    private static String idFor(String subject, Class<?> type) {
        return subject + "-" + type.getSimpleName();
    }

    // --- reading the database back ------------------------------------------------------------------------------

    /**
     * Every collection in the database holding a document that mentions {@code subject} in any value, at any depth.
     *
     * <p>Reads {@code getCollectionNames()} rather than the domain package, so a collection written by something that
     * is not a {@code @Document} class — GridFS, a migration, anything added later — is looked at too.</p>
     */
    private Set<String> collectionsMentioning(String subject) {
        Set<String> holding = new TreeSet<>();
        for (String collection : mongoTemplate.getCollectionNames()) {
            boolean found = mongoTemplate
                .findAll(org.bson.Document.class, collection)
                .stream()
                .anyMatch(document -> mentions(document, subject));
            if (found) {
                holding.add(collection);
            }
        }
        return holding;
    }

    /** Whether a BSON value carries the subject anywhere inside it. Substring, so an id built around it counts. */
    private static boolean mentions(Object value, String subject) {
        if (value instanceof String text) {
            return text.contains(subject);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(nested -> mentions(nested, subject));
        }
        if (value instanceof Iterable<?> many) {
            for (Object nested : many) {
                if (mentions(nested, subject)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> everyCollectionSeeded() {
        Set<String> names = DOCUMENT_CLASSES.stream().map(mongoTemplate::getCollectionName).collect(Collectors.toCollection(TreeSet::new));
        names.add(GRIDFS_FILES);
        return names;
    }

    private Set<String> declaredSurvivorCollections() {
        return SURVIVES_THE_ERASURE.keySet().stream().map(mongoTemplate::getCollectionName).collect(Collectors.toCollection(TreeSet::new));
    }

    // --- reflection ---------------------------------------------------------------------------------------------

    /** Every {@code @Document} class in the domain package. No filter on fields, deliberately — that was the defect. */
    private static List<Class<?>> everyDocumentClassInTheDomain() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.data.mongodb.core.mapping.Document.class));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("net.jojoaddison.domain")) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        found.sort(Comparator.comparing(Class::getSimpleName));
        return List.copyOf(found);
    }

    /** Every non-static, non-final {@code String} field on the class and its superclasses, ready to be written to. */
    private static List<java.lang.reflect.Field> writableStringFieldsOf(Class<?> type) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        for (Class<?> level = type; level != null && level != Object.class; level = level.getSuperclass()) {
            for (java.lang.reflect.Field field : level.getDeclaredFields()) {
                boolean writableString =
                    field.getType() == String.class && !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers());
                if (writableString) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static java.lang.reflect.Field idFieldOf(Class<?> type) {
        for (Class<?> level = type; level != null && level != Object.class; level = level.getSuperclass()) {
            for (java.lang.reflect.Field field : level.getDeclaredFields()) {
                if (field.isAnnotationPresent(org.springframework.data.annotation.Id.class)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new IllegalStateException(type.getName() + " has no @Id field, so this fixture cannot give it a stable id");
    }
}
