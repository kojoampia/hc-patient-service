package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.ServiceAccountToken;
import net.jojoaddison.service.GatewayAccountClient;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Change unit {@code 004}, the {@code Profile.accountId} backfill — backlog item 44.
 *
 * <h2>What these tests are for, and what they cannot be</h2>
 *
 * <p>The gateway is a mock here, because what is under test is the <em>migration's</em> behaviour: what it selects,
 * what it writes, what it refuses to write, and what it does on the four unhappy paths. The wire itself —
 * paging, the sort the gateway demands, the {@code Authorization} header, and the difference between a refusal and an
 * empty directory — is pinned against a real socket in {@code GatewayAccountClientTest}, because a mock can only ever
 * answer the question the test author already assumed.</p>
 *
 * <p>The assertion that matters most is <strong>{@link ProfileAccountIdBackfill#selectUnlinked()} after a pass</strong>.
 * Comparing documents cannot pin idempotence — a migration that re-selected the same records every run and wrote the
 * same value over them would leave them byte-identical and churn the collection on every start for ever. The selection
 * is the thing that has to go empty, which is why it is a method rather than an inlined query. 003 records the same
 * reasoning in the same words.</p>
 */
@IntegrationTest
class ProfileAccountIdBackfillIT {

    private static final String PROFILE = "profile";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProfileRepository profileRepository;

    private GatewayAccountClient gateway;
    private ServiceAccountToken tokens;
    private ProfileAccountIdBackfill backfill;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        gateway = mock(GatewayAccountClient.class);
        tokens = mock(ServiceAccountToken.class);
        when(tokens.administratorForAccountBackfill()).thenReturn("a.service.token");
        backfill = new ProfileAccountIdBackfill(mongoTemplate, gateway, tokens);
    }

    // --- the happy path, and the only assertion that pins idempotence ----------------------------------------------

    @Test
    void itLinksEveryProfileWhoseAddressTheGatewayKnows() {
        save("ama", "Ama@Example.test");
        save("kofi", "kofi@example.test");
        gatewayHolds(Map.of("ama@example.test", "u-1", "kofi@example.test", "u-2"));

        backfill.migrate();

        // Read back through the MAPPED type, not the raw document: that is what proves the stored key the migration
        // writes is the one @Field("account_id") reads, which a raw-Document round trip could not tell us.
        assertThat(profileRepository.findOneByAccountId("u-1")).map(Profile::getEmail).contains("Ama@Example.test");
        assertThat(profileRepository.findOneByAccountId("u-2")).map(Profile::getEmail).contains("kofi@example.test");
        assertThat(backfill.selectUnlinked()).isEmpty();
    }

    @Test
    void aSecondRunOverALinkedEstateAsksTheGatewayNothingAndMintsNoToken() {
        save("ama", "ama@example.test");
        gatewayHolds(Map.of("ama@example.test", "u-1"));
        backfill.migrate();

        backfill.migrate();

        // One token for the run that had work, none for the run that did not. A steady-state start issues no
        // ROLE_ADMIN credential at all, which is most of what keeps ServiceAccountToken narrow.
        verify(tokens, times(1)).administratorForAccountBackfill();
        verify(gateway, times(1)).accountDirectory(anyString());
        assertThat(profileRepository.findOneByAccountId("u-1")).isPresent();
    }

    // --- a partial run, and whether repeating one is safe ----------------------------------------------------------

    @Test
    void whatOneRunCouldNotFinishTheNextOnePicksUp() {
        save("ama", "ama@example.test");
        save("kofi", "kofi@example.test");

        // Standing in for every way a run ends halfway — there is no transaction, so this is a reachable state.
        gatewayHolds(Map.of("ama@example.test", "u-1"));
        backfill.migrate();
        assertThat(backfill.selectUnlinked()).hasSize(1);
        assertThat(profileRepository.findOneByAccountId("u-1")).isPresent();

        gatewayHolds(Map.of("ama@example.test", "u-1", "kofi@example.test", "u-2"));
        backfill.migrate();

        assertThat(backfill.selectUnlinked()).isEmpty();
        assertThat(profileRepository.findOneByAccountId("u-2")).map(Profile::getEmail).contains("kofi@example.test");
        // Repeating is safe as well as useful: the profile the first pass linked kept the value it was given.
        assertThat(profileRepository.findOneByAccountId("u-1")).map(Profile::getEmail).contains("ama@example.test");
    }

    // --- the unhappy paths, none of which may write or throw -------------------------------------------------------

    @Test
    void anUnconfiguredGatewayLeavesEveryProfileOutstandingAndMintsNothing() {
        save("ama", "ama@example.test");
        when(gateway.isConfigured()).thenReturn(false);

        backfill.migrate();

        verify(tokens, never()).administratorForAccountBackfill();
        verify(gateway, never()).accountDirectory(anyString());
        assertThat(backfill.selectUnlinked()).hasSize(1);
    }

    @Test
    void anUnreachableGatewayLeavesEveryProfileOutstanding() {
        save("ama", "ama@example.test");
        when(gateway.isConfigured()).thenReturn(true);
        // Empty means COULD NOT ASK. An empty directory would mean "no such account" and would be a different
        // outcome — the profiles would still be outstanding, but for a reason that will not change on a retry.
        when(gateway.accountDirectory(anyString())).thenReturn(Optional.empty());

        backfill.migrate();

        assertThat(backfill.selectUnlinked()).hasSize(1);
        assertThat(mongoTemplate.findAll(Document.class, PROFILE).getFirst()).doesNotContainKey("account_id");
    }

    // --- what it refuses to do ------------------------------------------------------------------------------------

    @Test
    void twoProfilesAreNeverGivenOneAccountId() {
        // Already a broken state — start() refuses a second profile for an address — but it is the state in which
        // guessing would be worst, because the identifier at stake is what hc-admin names a person by.
        save("first", "shared@example.test");
        save("second", "shared@example.test");
        gatewayHolds(Map.of("shared@example.test", "u-1"));

        backfill.migrate();

        assertThat(profileRepository.findAll().stream().filter(profile -> "u-1".equals(profile.getAccountId()))).hasSize(1);
        assertThat(backfill.selectUnlinked()).hasSize(1);
    }

    @Test
    void aProfileWithNoAddressIsPassedOverRatherThanMatchedOnNothing() {
        save("nameless", null);
        save("blank", "   ");
        gatewayHolds(Map.of("", "u-nobody"));

        backfill.migrate();

        assertThat(profileRepository.findAll()).allSatisfy(profile -> assertThat(profile.getAccountId()).isNull());
        assertThat(backfill.selectUnlinked()).hasSize(2);
    }

    @Test
    void aProfileThatAlreadyCarriesAnAccountIdIsNotSelectedAndNotRewritten() {
        Profile linked = profileRepository.save(
            new Profile().patientId("ama").email("ama@example.test").accountId("u-original").firstName("Ama")
        );
        // The gateway now says a different account holds that address. This migration fills blanks; it does not
        // arbitrate, and re-pointing an existing link is not something a startup job should do unsupervised.
        gatewayHolds(Map.of("ama@example.test", "u-different"));

        backfill.migrate();

        assertThat(profileRepository.findById(linked.getId()).orElseThrow().getAccountId()).isEqualTo("u-original");
        assertThat(backfill.selectUnlinked()).isEmpty();
    }

    @Test
    void anEmptyCollectionIsNotAnErrorAndAsksTheGatewayNothing() {
        backfill.migrate();

        verify(gateway, never()).isConfigured();
        verify(tokens, never()).administratorForAccountBackfill();
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    private void save(String patientId, String email) {
        profileRepository.save(new Profile().patientId(patientId).email(email).firstName(patientId));
    }

    private void gatewayHolds(Map<String, String> accountIdByEmail) {
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.accountDirectory(anyString()))
            .thenReturn(Optional.of(new GatewayAccountClient.AccountDirectory(accountIdByEmail, accountIdByEmail.size(), 0, true)));
    }

    /** Guards against the fixture silently drifting from what {@link #save} writes. */
    @Test
    void theFixtureWritesProfilesWithNoAccountIdAtAll() {
        save("ama", "ama@example.test");

        List<Document> raw = mongoTemplate.findAll(Document.class, PROFILE);

        assertThat(raw).hasSize(1);
        assertThat(raw.getFirst()).doesNotContainKey("account_id");
        assertThat(backfill.selectUnlinked()).hasSize(1);
    }
}
