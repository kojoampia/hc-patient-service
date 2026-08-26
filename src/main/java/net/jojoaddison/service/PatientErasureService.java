package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.jojoaddison.domain.ActivityLog;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.CareDelegation;
import net.jojoaddison.domain.CarePlanItem;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.Condition;
import net.jojoaddison.domain.Emergency;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.PaymentOption;
import net.jojoaddison.domain.PersonalDocument;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Report;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.Visitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.stereotype.Service;

/**
 * Erases everything this service holds about one patient.
 *
 * <p>Commissioned by {@link DeletionRequestService} on an administrator's word, never called from a patient-facing
 * path. It is the only code in the service that deletes clinical data in bulk, and the only thing standing between it
 * and the wrong record is the {@code patientId} it is handed.</p>
 *
 * <h2>Sixteen collections, named once</h2>
 *
 * <p>{@link #PATIENT_SCOPED} is the list, and it must stay exactly the set of {@code @Document} classes carrying a
 * {@code patient_id} field. A seventeenth collection added later and not added here is the failure mode that matters:
 * nothing breaks, the erasure reports success, and a patient told they were forgotten is not. {@code
 * PatientErasureServiceIT} asserts the list against the domain package by reflection so that omission fails a test
 * rather than a regulator's question.</p>
 *
 * <h2>It is not atomic, and is safe to re-run</h2>
 *
 * <p>MongoDB multi-document transactions need a replica set; this service runs against a standalone node in
 * development and in the quality stack, so sixteen deletes are sixteen operations and a failure can land between any
 * two of them. That is why {@link DeletionRequestService} marks the request {@code COMPLETED} only after this returns,
 * and why every delete here is keyed on {@code patientId} alone: running it again removes whatever the first run did
 * not, and removes nothing else. A half-finished erasure is a job still on the queue, not a corrupted state.</p>
 *
 * <p>{@link Profile} goes first, deliberately. It is what {@code PatientScope} resolves a token's email into, so once
 * it is gone the account resolves to no patient and sees nothing at all — a partial failure leaves the remainder
 * unreachable rather than leaving a patient looking at a record with half of itself missing.</p>
 *
 * <h2>What deliberately survives</h2>
 *
 * <p>The {@link net.jojoaddison.domain.DeletionRequest} itself, which is the evidence the erasure was asked for and
 * carried out. And the account in the gateway: {@code hc-patient-api} runs {@code skipUserManagement} and has no
 * {@code User} document to delete. Closing the account is a second step, in the gateway, by the same administrator —
 * see {@code DeletionRequestResource.complete}.</p>
 */
@Service
public class PatientErasureService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientErasureService.class);

    /**
     * Every collection keyed by {@code patient_id}, {@link Profile} first.
     *
     * <p>Order is otherwise irrelevant — there are no foreign keys to honour — so the rest is alphabetical to make an
     * omission visible when reading.</p>
     */
    static final List<Class<?>> PATIENT_SCOPED = List.of(
        Profile.class,
        ActivityLog.class,
        Address.class,
        Allergy.class,
        CareDelegation.class,
        CarePlanItem.class,
        ClinicalCase.class,
        Condition.class,
        Emergency.class,
        Medication.class,
        Membership.class,
        PersonalDocument.class,
        Report.class,
        Stat.class,
        Task.class,
        Visitation.class
    );

    /** Key under which {@link #erase} reports the GridFS objects it removed. */
    static final String REPORT_FILES_KEY = "reportFiles";

    /** Key under which {@link #erase} reports delegations removed because this person was somebody else's angel. */
    static final String DELEGATIONS_AS_ANGEL_KEY = "careDelegationAsAngel";

    private final MongoTemplate mongoTemplate;
    private final GridFsOperations gridFs;

    public PatientErasureService(MongoTemplate mongoTemplate, GridFsOperations gridFs) {
        this.mongoTemplate = mongoTemplate;
        this.gridFs = gridFs;
    }

    /**
     * Deletes every document this service holds for a patient, and every report file behind them.
     *
     * @param patientId the record to erase.
     * @param angelEmail the erased account's email, so that delegations it holds over <em>other</em> patients go too;
     *     null skips that step.
     * @return how many documents were removed, by collection name. Counts only — see
     *     {@link net.jojoaddison.domain.DeletionRequest#getErasedCounts()}.
     * @throws IllegalArgumentException if {@code patientId} is null or blank.
     */
    public Map<String, Long> erase(String patientId, String angelEmail) {
        // A blank id would build the query {patient_id: null}, which in MongoDB matches every document that has no
        // patient_id at all — and then deletes them. This guard is the difference between erasing one patient and
        // emptying sixteen collections, and it is why the check is an exception rather than an early return.
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("Refusing to erase: no patientId was given");
        }

        Map<String, Long> counts = new LinkedHashMap<>();

        // The files first, while the Report documents that point at them still exist. GridFS objects carry the
        // patientId in their metadata — recorded by ReportFileService precisely so a stray object can be traced back
        // — so they are reachable without walking the reports, but only until something removes the trail.
        Query filesOfPatient = Query.query(Criteria.where("metadata.patientId").is(patientId));
        long files = gridFs.find(filesOfPatient).into(new ArrayList<>()).size();
        gridFs.delete(filesOfPatient);
        counts.put(REPORT_FILES_KEY, files);

        for (Class<?> type : PATIENT_SCOPED) {
            Query byPatient = Query.query(Criteria.where("patient_id").is(patientId));
            long removed = mongoTemplate.remove(byPatient, type).getDeletedCount();
            counts.put(mongoTemplate.getCollectionName(type), removed);
        }

        // PaymentOption is patient data and is NOT in the list above, because it does not key on patient_id: its
        // field is user_id. The value is the same -- PaymentOptionResource sets it from
        // patientScope.requirePatientIdForWrite -- so it is the patientId wearing another name, and the loop above
        // simply cannot see it.
        //
        // Missing this meant a patient's payment details outliving the erasure they asked for, which is precisely
        // the failure this whole feature exists to prevent. Found by comparing this list against the sixteen
        // resources the DELETE lockdown covers rather than by anything failing.
        //
        // Handled separately rather than by teaching PATIENT_SCOPED about field names, because a list of types that
        // all key the same way is readable at a glance and a list of type-plus-field pairs is not -- and the value
        // of that list is that an omission is visible when reading it.
        Query byUserId = Query.query(Criteria.where("user_id").is(patientId));
        counts.put(
            mongoTemplate.getCollectionName(PaymentOption.class),
            mongoTemplate.remove(byUserId, PaymentOption.class).getDeletedCount()
        );

        // This person may also have been acting for somebody ELSE. Those rows are keyed by angel_email, not by
        // patient_id, so the loop above cannot see them — and leaving them would mean a deleted account still holding
        // an ACTIVE delegation over a patient who is very much still here. PatientScope reads this collection on
        // every acting-as request, so a stale row is access, not clutter.
        if (angelEmail != null && !angelEmail.isBlank()) {
            Query asAngel = Query.query(Criteria.where("angel_email").regex("^" + Pattern.quote(angelEmail) + "$", "i"));
            long removed = mongoTemplate.remove(asAngel, CareDelegation.class).getDeletedCount();
            counts.put(DELEGATIONS_AS_ANGEL_KEY, removed);
        }

        LOG.info("Erased patient {}: {}", patientId, counts);
        return counts;
    }
}
