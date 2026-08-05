package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Address;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Address entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AddressRepository extends MongoRepository<Address, String> {
    /** Addresses belonging to one patient. See {@link net.jojoaddison.security.PatientScope}. */
    List<Address> findByPatientId(String patientId);
}
