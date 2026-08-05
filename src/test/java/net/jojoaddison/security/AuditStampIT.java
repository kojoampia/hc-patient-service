package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Proves the audit trail cannot be written by the person it records.
 *
 * <p>Entities here declare their own {@code createdBy}/{@code modifiedBy}/{@code createdDate}/{@code modifiedDate}
 * and resources bind the domain object straight from the request body, so until 2026-08-05 all four were
 * attacker-controlled — a caller could attribute any record to any user and backdate it. For a health record system
 * the audit trail is what you reach for when investigating who changed what, and one the subject can rewrite answers
 * that question with whatever they chose to write in it.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuditStampIT {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PATIENT_ID = "patient-alice";
    private static final String ALICE_LOGIN = "alice";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AllergyRepository allergyRepository;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        allergyRepository.deleteAll();
        profileRepository.save(new Profile().email(ALICE_EMAIL).patientId(ALICE_PATIENT_ID));
    }

    @Test
    void creationIsAttributedToTheCallerNotToTheBody() throws Exception {
        String forged =
            """
            {"name":"peanut","createdBy":"somebody-else","modifiedBy":"somebody-else",
             "createdDate":"1999-01-01","modifiedDate":"1999-01-01"}
            """;

        restMockMvc
            .perform(post("/api/allergies").with(alice()).contentType(MediaType.APPLICATION_JSON).content(forged))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value(ALICE_LOGIN))
            .andExpect(jsonPath("$.modifiedBy").value(ALICE_LOGIN))
            .andExpect(jsonPath("$.createdDate").value(LocalDate.now().toString()));

        Allergy stored = allergyRepository.findAll().get(0);
        assertThat(stored.getCreatedBy()).isEqualTo(ALICE_LOGIN);
        assertThat(stored.getCreatedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void updateCannotRewriteWhoCreatedTheRecordOrWhen() throws Exception {
        Allergy existing = allergyRepository.save(
            new Allergy().patientId(ALICE_PATIENT_ID).name("peanut").createdBy("original-author").createdDate(LocalDate.of(2020, 1, 1))
        );

        String forged =
            """
            {"id":"%s","name":"peanut","patientId":"%s","createdBy":"somebody-else",
             "createdDate":"1999-01-01","modifiedBy":"somebody-else","modifiedDate":"1999-01-01"}
            """.formatted(existing.getId(), ALICE_PATIENT_ID);

        restMockMvc
            .perform(put("/api/allergies/{id}", existing.getId()).with(alice()).contentType(MediaType.APPLICATION_JSON).content(forged))
            .andExpect(status().isOk())
            // Creation facts survive untouched...
            .andExpect(jsonPath("$.createdBy").value("original-author"))
            .andExpect(jsonPath("$.createdDate").value("2020-01-01"))
            // ...and the modification is attributed to whoever actually made it.
            .andExpect(jsonPath("$.modifiedBy").value(ALICE_LOGIN))
            .andExpect(jsonPath("$.modifiedDate").value(LocalDate.now().toString()));

        Allergy stored = allergyRepository.findById(existing.getId()).orElseThrow();
        assertThat(stored.getCreatedBy()).isEqualTo("original-author");
        assertThat(stored.getCreatedDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(stored.getModifiedBy()).isEqualTo(ALICE_LOGIN);
    }

    private static RequestPostProcessor alice() {
        // The subject is the login; the email claim is what resolves the patient. Both matter here: the first is what
        // gets stamped, the second is what lets the write through at all.
        return jwt()
            .jwt(builder -> builder.subject(ALICE_LOGIN).claim(SecurityUtils.EMAIL_KEY, ALICE_EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
