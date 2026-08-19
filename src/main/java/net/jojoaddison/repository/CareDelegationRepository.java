package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the CareDelegation entity.
 *
 * <p>{@link #findOneByAngelEmailIgnoreCaseAndStatus} is on the authorization hot path — it runs for every request that
 * arrives with an {@code X-Acting-As} header — which is why {@code CareDelegation} carries a compound index on
 * {@code (angel_email, status)}.</p>
 */
@SuppressWarnings("unused")
@Repository
public interface CareDelegationRepository extends MongoRepository<CareDelegation, String> {
    /**
     * The delegation that lets this caller act for someone, if there is one.
     *
     * <p>Callers must pass {@link DelegationStatus#ACTIVE}. Status is a parameter rather than being baked into the
     * method name so that the portal can ask the same question about {@code PENDING} and {@code STANDBY} rows, but an
     * authorization check that passes anything else is a bug — see {@link net.jojoaddison.security.PatientScope}.</p>
     */
    Optional<CareDelegation> findOneByAngelEmailIgnoreCaseAndStatus(String angelEmail, DelegationStatus status);

    /** Every delegation naming this person as the angel, in any state — what the sign-in profile picker lists. */
    List<CareDelegation> findByAngelEmailIgnoreCase(String angelEmail);

    /** Every delegation over this patient's record, in any state — what the portal's delegation screen lists. */
    List<CareDelegation> findByPatientId(String patientId);

    List<CareDelegation> findByPatientIdAndStatus(String patientId, DelegationStatus status);
}
