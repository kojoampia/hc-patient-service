package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.PaymentOption;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PaymentOption entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PaymentOptionRepository extends MongoRepository<PaymentOption, String> {
    /**
     * Payment options belonging to one patient.
     *
     * <p>Keyed on {@code userID} rather than {@code patientId} because that is the owner field this entity already
     * had, and adding a second one would leave two competing notions of ownership. {@code userID} is undocumented:
     * if it holds a gateway login rather than a patient id, this scoping is wrong in a way that testing with a single
     * account will not reveal.</p>
     */
    List<PaymentOption> findByUserID(String userID);
}
