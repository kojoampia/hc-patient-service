package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.jojoaddison.security.ServiceAccountToken;
import net.jojoaddison.service.GatewayAccountClient;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Gives every existing profile the {@code accountId} that onboarding now stamps on a new one.
 *
 * <p>Backlog item 44. {@code Profile.accountId} is the patient gateway's {@code User.id} and is how hc-admin — or
 * anything else — will read identity out of this subsystem. Profiles written before the field existed have none, and
 * nothing in this service can derive one: the only place an address maps to a {@code User.id} is the gateway's
 * {@code GET /api/admin/users}. So this change unit asks.</p>
 *
 * <h2>It runs on every start, and that is the design rather than a convenience</h2>
 *
 * <p>{@code runAlways = true}, which 001, 002 and 003 are not, because this is the first change unit here whose work
 * depends on something outside the database being up. A once-only unit has exactly two behaviours when the gateway is
 * unreachable at the moment it runs, and both are wrong:</p>
 *
 * <ul>
 *   <li><strong>Throw</strong>, and Mongock records the unit as failed and the application does not start — so this
 *       service acquires a hard startup dependency on the gateway, in a stack where the two start together and the
 *       api is routinely up first.</li>
 *   <li><strong>Return quietly</strong>, and Mongock records it as <em>executed</em>. The backfill never runs again,
 *       every profile that existed at that moment is permanently unlinked, and repairing it means hand-editing
 *       {@code mongockChangeLog} in production.</li>
 * </ul>
 *
 * <p>Running always removes the choice: an unreachable gateway costs one warning and the work happens on the next
 * start. It is affordable because the selection is cheap and, once the estate is linked, empty — a steady-state
 * start does one indexed-shaped count, logs one line, and asks the gateway nothing. <strong>No service token is
 * minted when there is nothing to do</strong>, which is most of what keeps {@link ServiceAccountToken} narrow.</p>
 *
 * <h2>A partial run, and whether repeating one is safe</h2>
 *
 * <p>There is no transaction — production runs MongoDB standalone, with no replica set — so a run interrupted halfway
 * leaves some profiles linked and the rest not. That is a correct intermediate state, not a corrupt one, and it is
 * the same argument onboarding's five separate writes rest on. Repeating is safe and is the recovery: the selection
 * is <em>profiles with no account id</em>, so a second pass sees only what the first did not finish, writes the same
 * value it would have written, and touches nothing it already linked.</p>
 *
 * <p>Two profiles are never given one account id, and that is enforced twice over on purpose because the two halves
 * answer different questions. The check before each write asks <em>is this account already claimed</em>, which is a
 * data condition this pass can see and report; it is also what makes a re-run free rather than an exception storm.
 * {@link ProfileAccountIdUniqueIndex} — a partial unique index created by change unit {@code 003.5}, before this one
 * — answers <em>did somebody claim it since I looked</em>, which no check-then-act here can, because Mongo runs
 * standalone and there is no transaction to make the check and the write atomic. A patient onboarding while this runs
 * is a real possibility: Mongock's runner is an {@code ApplicationRunner}, so the service is already serving.</p>
 *
 * <h2>What "nothing to do" looks like, deliberately, at production log level</h2>
 *
 * <p>Item 40's change unit logged its clean no-op at {@code DEBUG} and returned, so at production log level a
 * migration that found nothing and a migration that never ran were byte-identical — a gap that cost a separate roll
 * and a read of {@code mongockChangeLog} to close. <strong>Every path out of {@link #migrate()} logs at {@code INFO}
 * or {@code WARN}</strong>, including the boring one, and every line begins with the same prefix so one grep
 * distinguishes all five outcomes: linked, nothing to do, no gateway configured, gateway did not answer, and linked
 * some with a remainder outstanding.</p>
 *
 * <h2>What it does not do</h2>
 *
 * <p><strong>It does not touch the eighteen collections keyed by {@code patient_id}, and it does not move any
 * authorization onto {@code accountId}.</strong> {@code PatientScope} still resolves a caller by email to a
 * {@code patientId}, exactly as before. Translating the children is item 53 and removing {@code patientId} is item
 * 54; they are separate because an erasure guard in this repository discovers its own scope by scanning for a
 * {@code patient_id} field, and has already been shown to go blind to that rename with eight tests still green.</p>
 *
 * <p><strong>It announces nothing on {@code patient-events}.</strong> A change unit runs before anything is serving
 * and has no business publishing, which is the answer 003 gives in the same words.</p>
 *
 * <p><strong>It does not restamp {@code modified_by} or {@code modified_date}.</strong> Those say who last touched
 * the record; overwriting them to say "a migration did" loses a fact to duplicate one Mongock already holds.</p>
 */
@ChangeUnit(id = "profile-account-id-backfill", order = "004", author = "hc-patient", runAlways = true)
public class ProfileAccountIdBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileAccountIdBackfill.class);

    /** One prefix on every line, so a single grep of a production log distinguishes all five outcomes. */
    private static final String TAG = "Profile account-id backfill:";

    private static final String PROFILE = "profile";
    private static final String ACCOUNT_ID = "account_id";
    private static final String EMAIL = "email";
    private static final String ID = "_id";

    private final MongoTemplate mongoTemplate;
    private final GatewayAccountClient gatewayAccountClient;
    private final ServiceAccountToken serviceAccountToken;

    public ProfileAccountIdBackfill(
        MongoTemplate mongoTemplate,
        GatewayAccountClient gatewayAccountClient,
        ServiceAccountToken serviceAccountToken
    ) {
        this.mongoTemplate = mongoTemplate;
        this.gatewayAccountClient = gatewayAccountClient;
        this.serviceAccountToken = serviceAccountToken;
    }

    @Execution
    public void migrate() {
        List<Document> unlinked = selectUnlinked();
        if (unlinked.isEmpty()) {
            // "No profile is missing one" rather than "every profile has one", because the two differ on an empty
            // collection and only the first is true of it. This line is the one that has to be here: it is what
            // makes a clean no-op distinguishable from a change unit that never ran at all.
            LOG.info("{} no profile is missing an account id; nothing to do", TAG);
            return;
        }

        if (!gatewayAccountClient.isConfigured()) {
            LOG.warn(
                "{} {} profile(s) carry no account id and no patient gateway is configured " +
                "(application.gateway.base-url); this will be retried on the next start",
                TAG,
                unlinked.size()
            );
            return;
        }

        // Minted here rather than in the constructor, and only past the two returns above: a start with nothing
        // outstanding issues no ROLE_ADMIN token at all. See ServiceAccountToken.
        Optional<GatewayAccountClient.AccountDirectory> answer = gatewayAccountClient.accountDirectory(
            serviceAccountToken.administratorForAccountBackfill()
        );
        if (answer.isEmpty()) {
            LOG.warn(
                "{} {} profile(s) carry no account id and the gateway did not answer; this will be retried on the next start",
                TAG,
                unlinked.size()
            );
            return;
        }
        GatewayAccountClient.AccountDirectory directory = answer.orElseThrow();
        if (directory.ambiguousEmails() > 0) {
            LOG.warn(
                "{} {} address(es) are held by more than one gateway account and were left unmatched — " +
                "linking either would be choosing one on no evidence",
                TAG,
                directory.ambiguousEmails()
            );
        }

        int linked = 0;
        int noEmail = 0;
        int noAccount = 0;
        int alreadyHeld = 0;
        int lostRace = 0;
        for (Document profile : unlinked) {
            Object stored = profile.get(EMAIL);
            String email = stored == null ? "" : String.valueOf(stored).trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                // Named nobody, so it can be matched with nothing. Blank counts as absent, the reading
                // PatientEventPublisher applies to a subject key and 003 applies to an owner.
                noEmail++;
                continue;
            }
            String accountId = directory.accountIdByEmail().get(email);
            if (accountId == null) {
                noAccount++;
                continue;
            }
            if (mongoTemplate.exists(Query.query(Criteria.where(ACCOUNT_ID).is(accountId)), PROFILE)) {
                // Two profiles at one address, or a rerun racing itself. Either way the second one is a claim this
                // migration cannot settle, and the identifier at stake is the one hc-admin will name a person by.
                alreadyHeld++;
                continue;
            }
            try {
                // The raw _id, handed straight back rather than stringified: it is an ObjectId for anything this
                // service wrote and a String for the seeded fixtures, and a criterion built from the printed form of
                // the first matches nothing while reporting a successful no-op. That cost time during item 40's
                // recovery.
                mongoTemplate.updateFirst(
                    Query.query(Criteria.where(ID).is(profile.get(ID))),
                    new Update().set(ACCOUNT_ID, accountId),
                    PROFILE
                );
                linked++;
            } catch (DuplicateKeyException lost) {
                // The race ProfileAccountIdUniqueIndex exists to make visible: a patient finished onboarding for this
                // same account between the check above and this write. The index refused the second write, which is
                // the correct outcome — the profile stays unlinked and the next start reconsiders it. Counted apart
                // from alreadyHeld because the two are different things: that one is a data condition this pass could
                // see, this one is a timing condition it could not.
                lostRace++;
            }
        }

        LOG.info(
            "{} linked {} of {} profile(s) from {} gateway account(s){}",
            TAG,
            linked,
            unlinked.size(),
            directory.accountsSeen(),
            directory.complete() ? "" : " (the directory was truncated before the gateway ran out of accounts)"
        );
        int outstanding = unlinked.size() - linked;
        if (outstanding > 0) {
            LOG.warn(
                "{} {} profile(s) are still unlinked — {} carry no email, {} match no gateway account, {} name an " +
                "account another profile already holds, {} lost a race with a concurrent onboarding; they will be " +
                "reconsidered on the next start",
                TAG,
                outstanding,
                noEmail,
                noAccount,
                alreadyHeld,
                lostRace
            );
        }
    }

    /**
     * The profiles this migration still has work to do on: those with no {@code account_id}.
     *
     * <p>Package-private so a test can assert it is <b>empty after a pass</b>, which is the assertion that pins
     * idempotence — comparing documents cannot, for the reason 003 sets out at length.</p>
     *
     * <p>Read as raw {@link Document}s against the collection name rather than through {@code ProfileRepository}:
     * {@code account_id} and {@code email} are the stored spellings, and a migration that went through the mapped
     * type would be describing the model rather than the data. It also cannot be tripped by a document the mapped
     * type would refuse — the state 001 and 002 both exist to clear up.</p>
     *
     * <p>{@code null} matches a missing key in MongoDB as well as a stored null, so the two criteria below cover
     * absent, null and blank. Only {@code _id} and {@code email} are read back; nothing else is needed and a profile
     * is a wide document.</p>
     *
     * @return the unlinked profiles, in the collection's natural order.
     */
    List<Document> selectUnlinked() {
        Query unlinked = Query.query(new Criteria().orOperator(Criteria.where(ACCOUNT_ID).is(null), Criteria.where(ACCOUNT_ID).is("")));
        unlinked.fields().include(ID).include(EMAIL);
        return mongoTemplate.find(unlinked, Document.class, PROFILE);
    }

    /**
     * Deliberately does nothing — the answer 001, 002 and 003 all give, and here it is close to meaningless anyway.
     *
     * <p>Unsetting {@code account_id} would take the estate's join key off every profile and leave
     * {@code GET /api/profile/{accountId}} answering 404 for everybody, to undo a link that is a fact about the
     * gateway rather than a change of shape. And with {@code runAlways} the very next start would put it all back.</p>
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn("{} not rolled back automatically — see the class comment", TAG);
    }
}
