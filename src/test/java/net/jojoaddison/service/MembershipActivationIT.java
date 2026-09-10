package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That {@code activateIfPending} really is conditional — against a database, because nothing else can tell.
 *
 * <h2>Why this is an IT and not three more lines in {@code MembershipStatusAnnouncementTest}</h2>
 *
 * <p>Because the guard under test <em>is</em> the query. {@code MembershipStatusAnnouncementTest} mocks
 * {@code MongoTemplate}, so it asserts what this service does with the answer it is given and cannot see what it
 * asked. Delete {@code .and("status").is(PENDING)} from the criterion and every unit test in this repository stays
 * green while the consumer silently overwrites whatever an administrator decided — which is item 30's defect with a
 * sibling product holding the pen, and the exact shape item 19's own record warns about: <em>"the idempotence test
 * was vacuous when it was certified sound … all five tests stayed green with the bug present."</em></p>
 *
 * <p>The mutation that must fail here is that one line. It does.</p>
 *
 * <p>Backlog item 19.</p>
 */
@IntegrationTest
class MembershipActivationIT {

    private static final String PATIENT_ID = "patient-activation";

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private MembershipRepository membershipRepository;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
    }

    private Membership stored(MembershipStatus status) {
        return membershipRepository.save(new Membership().patientId(PATIENT_ID).plan("PAWPAW").name("PAWPAW Plan").status(status));
    }

    @Test
    void aPendingMembershipIsActivated() {
        Membership pending = stored(MembershipStatus.PENDING);

        assertThat(membershipService.activateIfPending(pending.getId()))
            .get()
            .extracting(Membership::getStatus)
            // returnNew: the document AFTER the write, because announceChosenPlan reports what was persisted and
            // would publish PENDING off the pre-image.
            .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void aCancelledMembershipIsLeftExactlyAsItWas() {
        // The mutation test. An administrator cancelled this membership; a stale acknowledgement must not resurrect
        // it. Read-then-save would: the consumer's read said PENDING a moment ago, and the save replaces wholesale.
        Membership cancelled = stored(MembershipStatus.CANCELLED);

        assertThat(membershipService.activateIfPending(cancelled.getId()))
            .as("a membership that is not PENDING was activated — the criterion is not doing its job")
            .isEmpty();

        assertThat(membershipRepository.findById(cancelled.getId()).orElseThrow().getStatus())
            .as("the stored status was overwritten, which is the decision-erasing write item 30 closed")
            .isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void anAlreadyActiveMembershipIsNotActivatedTwice() {
        // What a replay would meet had the event-id ledger not caught it first. It answers empty rather than writing
        // ACTIVE over ACTIVE, so no second announcement goes out even by that route.
        Membership active = stored(MembershipStatus.ACTIVE);

        assertThat(membershipService.activateIfPending(active.getId())).isEmpty();
    }

    @Test
    void aMembershipThatIsNotThereIsNotAnError() {
        // Empty, not an exception: the caller turns it into a refusal with a reason, and a missing document and a
        // moved one are the same answer to the same question.
        assertThat(membershipService.activateIfPending("no-such-membership")).isEmpty();
    }
}
