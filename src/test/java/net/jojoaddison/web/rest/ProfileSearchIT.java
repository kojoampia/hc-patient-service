package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Searching the patient directory.
 *
 * <p>Runs as {@code ROLE_ADMIN}, which {@link net.jojoaddison.security.PatientScope} treats as unrestricted — the
 * caller the search exists for, since an administrator has no record of their own and reaches a patient by finding
 * them. The scoped side is asserted separately at the bottom, and is the half that matters most: a search must
 * narrow within the caller's scope rather than escape it.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = { "ROLE_ADMIN" })
class ProfileSearchIT {

    private static final String API = "/api/profiles";

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void initTest() {
        profileRepository.deleteAll();
        profileRepository.save(
            new Profile()
                .patientId("patient-1")
                .firstName("Kojo")
                .lastName("Ampia-Addison")
                .email("kojo@jac.net")
                .mobilePhone("(024) 555 0199")
        );
        profileRepository.save(
            new Profile().patientId("patient-2").firstName("Ama").lastName("Mensah").email("ama@example.test").mobilePhone("0201234567")
        );
        profileRepository.save(
            new Profile().patientId("qs-pat-0100").firstName("Esi").middleNames("Nana").lastName("Boateng").email("esi@example.invalid")
        );
    }

    @Test
    void findsByFirstName() throws Exception {
        mockMvc
            .perform(get(API + "?search=kojo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].email").value("kojo@jac.net"));
    }

    @Test
    void findsByLastNameCaseInsensitivelyAndPartially() throws Exception {
        mockMvc.perform(get(API + "?search=MENSA")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(1)));
    }

    @Test
    void findsByMiddleName() throws Exception {
        mockMvc
            .perform(get(API + "?search=nana"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].firstName").value("Esi"));
    }

    @Test
    void findsByEmailAndByPatientId() throws Exception {
        mockMvc.perform(get(API + "?search=example.invalid")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(1)));

        mockMvc
            .perform(get(API + "?search=qs-pat-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].patientId").value("qs-pat-0100"));
    }

    @Test
    void findsByPhoneNumberWrittenWithBrackets() throws Exception {
        // Unescaped this is an unbalanced group, and reaches Mongo as a syntax error rather than as a search. It is
        // also simply how people write phone numbers.
        mockMvc
            .perform(get(API).param("search", "(024) 555"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].firstName").value("Kojo"));
    }

    @Test
    void aWildcardMatchesNobodyRatherThanEverybody() throws Exception {
        // The reason the term is escaped. Unescaped, this returns the entire patient directory to anyone who types
        // two characters — an authorization boundary stepped around by a query language.
        mockMvc.perform(get(API + "?search=.*")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }

    @Test
    void aBlankSearchIsTheUnfilteredList() throws Exception {
        // Rather than "no results". An empty search box has not asked a question, and answering it with nothing
        // would make the finder look broken before the first keystroke.
        mockMvc.perform(get(API + "?search=")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(3)));

        // Through param() rather than in the URL: MockMvc does not URL-decode a query string, so "?search=%20%20"
        // would bind the literal six characters and test the opposite of what it reads as.
        mockMvc.perform(get(API).param("search", "   ")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(3)));
    }

    @Test
    void noMatchIsAnEmptyListRatherThanAnError() throws Exception {
        mockMvc.perform(get(API + "?search=nobodybythatname")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }

    @Test
    void searchIsPaged() throws Exception {
        mockMvc
            .perform(get(API + "?search=a&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(Matchers.lessThanOrEqualTo(2))));
    }

    @Test
    @WithMockUser(username = "ama@example.test", authorities = { "ROLE_USER", "ROLE_PATIENT" })
    void aPatientCannotSearchTheirWayToSomebodyElse() throws Exception {
        // The half that matters. Search reaches searchWithinPatient for a scoped caller, which carries their own
        // patient_id in the query — so a search is a narrowing inside their scope and never a way out of it.
        //
        // This caller has no JWT and so no resolvable email claim, which PatientScope reads as "no patient at all":
        // the strictest scope there is, and the right one to prove the search does not bypass. Kojo's name is in the
        // database and must not come back.
        mockMvc.perform(get(API + "?search=kojo")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));

        mockMvc.perform(get(API + "?search=.*")).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }
}
