package net.jojoaddison.repository;

import net.jojoaddison.domain.Membership;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Membership entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MembershipRepository extends MongoRepository<Membership, String> {}
