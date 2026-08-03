package net.jojoaddison.repository;

import net.jojoaddison.domain.PaymentOption;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PaymentOption entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PaymentOptionRepository extends MongoRepository<PaymentOption, String> {}
