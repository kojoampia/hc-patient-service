package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Profile entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<Profile> findByPatientId(String patientId);

    Page<Profile> findByPatientId(String patientId, Pageable pageable);

    /**
     * The signed-in user's own profile. The gateway issues tokens keyed on email, and email is the
     * only thing the dashboard knows about the person before it has loaded anything.
     */
    Optional<Profile> findOneByEmailIgnoreCase(String email);

    /**
     * Finds people by any of the things somebody would type looking for them.
     *
     * <p>Six fields, because a person is looked for by whichever of them the searcher happens to have: a name they
     * were told, an address on an email, a number on a form, or the id printed on a record.</p>
     *
     * <p><strong>{@code ?0} must already be regex-escaped by the caller.</strong> It is interpolated straight into a
     * {@code $regex}, so an unescaped {@code .*} would match every patient in the system and an unescaped
     * {@code (a+)+$} would hand the database a catastrophic backtrack. {@code ProfileSearch.escape} is the one place
     * that is done, and it is exercised by tests that search for literal metacharacters.</p>
     *
     * <p>Written as an explicit {@code @Query} rather than a derived
     * {@code findByFirstNameContainingIgnoreCaseOr…} chain of six: the derived form's escaping behaviour is a
     * property of the Spring Data version rather than something visible here, and this query's whole safety argument
     * rests on knowing exactly what reaches Mongo.</p>
     */
    @Query(
        "{ '$or': [ " +
        "{ 'first_name':   { $regex: ?0, $options: 'i' } }, " +
        "{ 'middle_names': { $regex: ?0, $options: 'i' } }, " +
        "{ 'last_name':    { $regex: ?0, $options: 'i' } }, " +
        "{ 'email':        { $regex: ?0, $options: 'i' } }, " +
        "{ 'mobile_phone': { $regex: ?0, $options: 'i' } }, " +
        "{ 'patient_id':   { $regex: ?0, $options: 'i' } } ] }"
    )
    Page<Profile> search(String escapedTerm, Pageable pageable);

    /**
     * The same search, confined to one patient.
     *
     * <p>The scoped arm of the list endpoint. A patient has one profile, so this can only ever return their own
     * record or nothing — which is the point: the search must narrow within the caller's scope rather than escape
     * it, and a scoped caller who searches must not reach a query that has no {@code patient_id} in it.</p>
     */
    @Query(
        "{ 'patient_id': ?0, '$or': [ " +
        "{ 'first_name':   { $regex: ?1, $options: 'i' } }, " +
        "{ 'middle_names': { $regex: ?1, $options: 'i' } }, " +
        "{ 'last_name':    { $regex: ?1, $options: 'i' } }, " +
        "{ 'email':        { $regex: ?1, $options: 'i' } }, " +
        "{ 'mobile_phone': { $regex: ?1, $options: 'i' } }, " +
        "{ 'patient_id':   { $regex: ?1, $options: 'i' } } ] }"
    )
    Page<Profile> searchWithinPatient(String patientId, String escapedTerm, Pageable pageable);
}
