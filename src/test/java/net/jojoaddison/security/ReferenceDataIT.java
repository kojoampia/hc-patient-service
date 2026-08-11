package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The entities that are reference data rather than patient records: readable by anyone signed in, writable only by
 * staff. DutyRoster and Shift joined them when the professional dashboard's demo dataset gave
 * {@code ClinicalCase.assignedRosterId} something to point at — who is on duty is staff data, and a patient reading
 * the care-team panel must not be able to rewrite it.
 *
 * <p>Until 2026-08-05 they were governed by the same single rule as everything else — "is authenticated" — so any
 * patient could rewrite the clinical staff directory, retitle a clinical recommendation, or delete a care team. They
 * cannot be scoped by {@code patientId} the way patient records are, because they do not belong to a patient; the
 * control is the verb, not the owner.</p>
 *
 * <p>Also covers the redaction of clinician contact details. The dashboard's care-team panel renders name, role,
 * initials and location and never reads email or phone number, so serving those to every caller only ever furnished
 * a staff directory complete with direct lines — to anyone who registered, on a site where registration is open.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReferenceDataIT {

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfessionalRepository professionalRepository;

    private Professional professional;

    @BeforeEach
    void setUp() {
        professionalRepository.deleteAll();
        professional =
            professionalRepository.save(
                new Professional()
                    .firstName("Grace")
                    .lastName("Mensah")
                    .role("Cardiologist")
                    .email("grace.mensah@example.com")
                    .phoneNumber("+233200000000")
                    .location("Accra")
            );
    }

    @ParameterizedTest(name = "{0} is readable by a patient but not writable")
    @CsvSource({ "/api/professionals", "/api/teams", "/api/recommendations", "/api/metadata", "/api/duty-rosters", "/api/shifts" })
    void referenceDataIsReadOnlyForPatients(String apiPath) throws Exception {
        // Readable: the care-team panel and the case screens depend on it.
        restMockMvc.perform(get(apiPath).with(patient())).andExpect(status().isOk());

        // Not writable, by any verb.
        restMockMvc
            .perform(post(apiPath).with(patient()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        restMockMvc
            .perform(
                put(apiPath + "/{id}", "any-id").with(patient()).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"any-id\"}")
            )
            .andExpect(status().isForbidden());
        restMockMvc
            .perform(
                patch(apiPath + "/{id}", "any-id")
                    .with(patient())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"any-id\"}")
            )
            .andExpect(status().isForbidden());
        restMockMvc.perform(delete(apiPath + "/{id}", "any-id").with(patient())).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} is writable by a clinician")
    @CsvSource({ "/api/teams", "/api/recommendations", "/api/duty-rosters", "/api/shifts" })
    void referenceDataStaysWritableForStaff(String apiPath) throws Exception {
        // The rule is the role, not a blanket lock — staff must still be able to maintain this data.
        restMockMvc
            .perform(post(apiPath).with(professionalCaller()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isCreated());
    }

    @Test
    void patientDoesNotSeeAClinicianEmailOrPhoneNumber() throws Exception {
        restMockMvc
            .perform(get("/api/professionals").with(patient()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[0].lastName").value("Mensah"))
            .andExpect(jsonPath("$.[0].role").value("Cardiologist"))
            .andExpect(jsonPath("$.[0].location").value("Accra"))
            .andExpect(jsonPath("$.[0].email").doesNotExist())
            .andExpect(jsonPath("$.[0].phoneNumber").doesNotExist());

        restMockMvc
            .perform(get("/api/professionals/{id}", professional.getId()).with(patient()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void staffDoStillSeeContactDetails() throws Exception {
        restMockMvc
            .perform(get("/api/professionals").with(professionalCaller()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[0].email").value("grace.mensah@example.com"))
            .andExpect(jsonPath("$.[0].phoneNumber").value("+233200000000"));
    }

    @Test
    void redactionDoesNotDamageTheStoredDocument() throws Exception {
        // The redacted view is a copy. Mutating what the repository handed back would be invisible here today and a
        // cache-poisoning bug the moment anything caches.
        restMockMvc.perform(get("/api/professionals").with(patient())).andExpect(status().isOk());

        Professional stored = professionalRepository.findById(professional.getId()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("grace.mensah@example.com");
        assertThat(stored.getPhoneNumber()).isEqualTo("+233200000000");
    }

    private static RequestPostProcessor patient() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "patient@example.com"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    private static RequestPostProcessor professionalCaller() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, "doctor@example.com"))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.PROFESSIONAL));
    }
}
