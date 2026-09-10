package net.jojoaddison.repository;

import net.jojoaddison.domain.PlanVerification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the {@link PlanVerification} record.
 *
 * <p>No query methods, and there should not be any: the event id is the document id, so
 * {@code existsById}/{@code insert} are the whole interface this is used through. See
 * {@link PlanVerification} for why the key is the event id.</p>
 */
@SuppressWarnings("unused")
@Repository
public interface PlanVerificationRepository extends MongoRepository<PlanVerification, String> {}
