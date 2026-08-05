package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link PatientScope}.
 *
 * <p>{@code PatientScopeIT} proves the rules hold through the HTTP layer; this covers the decision table itself,
 * including the branches an end-to-end test reaches awkwardly or not at all — an unrestricted caller with no filter,
 * a caller whose profile predates the {@code patientId} field, and a principal that is not a JWT at all.</p>
 */
class PatientScopeUnitTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    private ProfileRepository profileRepository;
    private PatientScope patientScope;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        patientScope = new PatientScope(profileRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- resolving identity ----------------------------------------------------------------------------------

    @Test
    void resolvesThePatientIdFromTheEmailClaim() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.currentPatientId()).contains("patient-1");
    }

    @Test
    void fallsBackToTheProfileIdWhenPatientIdWasNeverSet() {
        // Profiles written before the patientId field existed. The dashboard applies the same fallback, so both
        // sides have to agree on who a person is or the fix would lock those users out of their own records.
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile(null, "profile-1"));

        assertThat(patientScope.currentPatientId()).contains("profile-1");
    }

    @Test
    void resolvesToNobodyWhenThereIsNoProfile() {
        authenticateAs("stranger@example.com", AuthoritiesConstants.USER);
        when(profileRepository.findOneByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThat(patientScope.currentPatientId()).isEmpty();
    }

    @Test
    void resolvesToNobodyWhenThePrincipalIsNotAJwt() {
        // Anything that is not a bearer token carries no email claim — a @WithMockUser in a test, for instance.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("alice", "x", List.of()));
        SecurityContextHolder.setContext(context);

        assertThat(patientScope.currentPatientId()).isEmpty();
        verify(profileRepository, never()).findOneByEmailIgnoreCase(anyString());
    }

    // --- list scoping ----------------------------------------------------------------------------------------

    @Test
    void unrestrictedCallerWithNoFilterGetsEverything() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.findScoped(null, () -> List.of("all"), id -> List.of("scoped"))).containsExactly("all");
    }

    @Test
    void unrestrictedCallerWithAFilterGetsThatPatient() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.findScoped("patient-9", () -> List.of("all"), id -> List.of(id))).containsExactly("patient-9");
    }

    @Test
    void patientIsScopedToTheirOwnRecordsEvenWithNoFilter() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.findScoped(null, () -> List.of("all"), id -> List.of(id))).containsExactly("patient-1");
    }

    @Test
    void patientAskingForAnotherPatientGetsNothing() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.findScoped("patient-2", () -> List.of("all"), id -> List.of(id))).isEmpty();
    }

    @Test
    void callerWithNoProfileGetsNothingRatherThanEverything() {
        authenticateAs("stranger@example.com", AuthoritiesConstants.USER);
        when(profileRepository.findOneByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThat(patientScope.findScoped(null, () -> List.of("all"), id -> List.of(id))).isEmpty();
    }

    // --- paged scoping ---------------------------------------------------------------------------------------

    @Test
    void pagedUnrestrictedCallerWithNoFilterGetsEverything() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        Page<String> page = patientScope.findScopedPage(null, PAGE, p -> pageOf("all"), (id, p) -> pageOf("scoped"));
        assertThat(page.getContent()).containsExactly("all");
    }

    @Test
    void pagedUnrestrictedCallerWithAFilterGetsThatPatient() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        Page<String> page = patientScope.findScopedPage("patient-9", PAGE, p -> pageOf("all"), (id, p) -> pageOf(id));
        assertThat(page.getContent()).containsExactly("patient-9");
    }

    @Test
    void pagedPatientIsScopedToTheirOwnRecords() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        Page<String> page = patientScope.findScopedPage(null, PAGE, p -> pageOf("all"), (id, p) -> pageOf(id));
        assertThat(page.getContent()).containsExactly("patient-1");
    }

    @Test
    void pagedPatientAskingForAnotherPatientGetsAnEmptyPage() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.findScopedPage("patient-2", PAGE, p -> pageOf("all"), (id, p) -> pageOf(id))).isEmpty();
    }

    @Test
    void pagedCallerWithNoProfileGetsAnEmptyPage() {
        authenticateAs("stranger@example.com", AuthoritiesConstants.USER);
        when(profileRepository.findOneByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThat(patientScope.findScopedPage(null, PAGE, p -> pageOf("all"), (id, p) -> pageOf(id))).isEmpty();
    }

    // --- visibility ------------------------------------------------------------------------------------------

    @Test
    void ownRecordIsVisibleAndOtherPatientsAreNot() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.isVisible("patient-1")).isTrue();
        assertThat(patientScope.isVisible("patient-2")).isFalse();
    }

    @Test
    void anUnownedRecordIsVisibleToNoPatient() {
        // Documents written before patientId existed. Treating a null owner as "everyone's" would reopen the hole.
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.isVisible(null)).isFalse();
    }

    @Test
    void everythingIsVisibleToAnUnrestrictedCaller() {
        authenticateAs("doctor@example.com", AuthoritiesConstants.PROFESSIONAL);

        assertThat(patientScope.isVisible("patient-1")).isTrue();
        assertThat(patientScope.isVisible(null)).isTrue();
    }

    // --- writes ----------------------------------------------------------------------------------------------

    @Test
    void creatingStampsTheCallersOwnIdWhateverTheBodySays() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.requirePatientIdForWrite("patient-2")).isEqualTo("patient-1");
    }

    @Test
    void creatingKeepsTheBodyValueForAnUnrestrictedCaller() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.requirePatientIdForWrite("patient-2")).isEqualTo("patient-2");
    }

    @Test
    void unrestrictedCallerMayCreateARecordWithNoOwnerAtAll() {
        // Regression: this used to throw. requirePatientIdForWrite went through an Optional, and
        // Optional.ofNullable(null) made "the admin supplied no owner" indistinguishable from "this caller may not
        // write at all" — which 403'd an administrator creating an Address filed against no particular patient.
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.requirePatientIdForWrite(null)).isNull();
    }

    @Test
    void creatingIsRefusedWhenTheCallerIsNobody() {
        authenticateAs("stranger@example.com", AuthoritiesConstants.USER);
        when(profileRepository.findOneByEmailIgnoreCase("stranger@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientScope.requirePatientIdForWrite(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updatingPinsAPatientToTheStoredOwner() {
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.patientIdForUpdate("patient-1", "patient-2")).isEqualTo("patient-1");
    }

    @Test
    void updatingLetsAnUnrestrictedCallerRefileARecord() {
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.patientIdForUpdate("patient-1", "patient-2")).isEqualTo("patient-2");
        assertThat(patientScope.patientIdForUpdate("patient-1", null)).isEqualTo("patient-1");
    }

    @Test
    void anUnrestrictedCallerIsConfinedToNoPatient() {
        // Empty here means "not confined", which is why currentPatientId() must never be read on its own — the same
        // empty Optional means "nobody" for a patient with no profile.
        authenticateAs("admin@example.com", AuthoritiesConstants.ADMIN);

        assertThat(patientScope.currentPatientId()).isEmpty();
    }

    @Test
    void patientMayAskForItsOwnPatientIdExplicitly() {
        // The dashboard does exactly this: it resolves its profile, then passes ?patientId= on every collection call.
        // If the matching filter were rejected along with the mismatching one, the whole portal would render empty.
        authenticateAs("alice@example.com", AuthoritiesConstants.USER);
        whenProfileFor("alice@example.com", profile("patient-1", "profile-1"));

        assertThat(patientScope.findScoped("patient-1", () -> List.of("all"), id -> List.of(id))).containsExactly("patient-1");
        assertThat(patientScope.findScopedPage("patient-1", PAGE, p -> pageOf("all"), (id, p) -> pageOf(id)).getContent())
            .containsExactly("patient-1");
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static void authenticateAs(String email, String authority) {
        Jwt jwt = new Jwt("token", null, null, Map.of("alg", "HS512"), Map.of("sub", email, SecurityUtils.EMAIL_KEY, email));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
        SecurityContextHolder.setContext(context);
    }

    private void whenProfileFor(String email, Profile profile) {
        when(profileRepository.findOneByEmailIgnoreCase(email)).thenReturn(Optional.of(profile));
    }

    private static Profile profile(String patientId, String id) {
        Profile profile = new Profile().patientId(patientId);
        profile.setId(id);
        return profile;
    }

    private static Page<String> pageOf(String value) {
        return new PageImpl<>(List.of(value), PAGE, 1);
    }
}
