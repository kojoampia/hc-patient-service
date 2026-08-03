package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.PersonalDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the PersonalDocument entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PersonalDocumentRepository extends MongoRepository<PersonalDocument, String> {
    /**
     * Records belonging to one patient. Every portal screen is scoped this way, so the filter
     * belongs in the query rather than in the caller.
     */
    List<PersonalDocument> findByPatientId(String patientId);

    Page<PersonalDocument> findByPatientId(String patientId, Pageable pageable);
}
