package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every clinical record can be archived, and the rules are the same for all of them.
 *
 * <h2>Why one parameterised class rather than ten copies</h2>
 *
 * <p>Archiving arrived on {@code ClinicalCase} on 2026-08-22 and on nothing else, so fifteen resources named a rule
 * — patient data is never deleted — with no way to obey it. Ten of them are clinical records and got it on
 * 2026-08-24; the five administrative ones did not, deliberately, because retiring a {@code Profile} or a
 * {@code PaymentOption} is a different act needing a separate decision.</p>
 *
 * <p>The behaviour lives once, in {@code ArchiveSupport}, so the risk is not that one entity behaves differently by
 * design — it is that one was wired up wrongly and nobody noticed. A table-driven test fails loudly for the single
 * entity that was missed, which ten hand-written classes would not: the tenth would simply never be written.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ArchiveEveryClinicalRecordIT {

    @Autowired
    private MockMvc mockMvc;

    /** Every path that gained archiving, with a discipline that may write its domain and one that may not. */
    private static Stream<Arguments> clinicalRecords() {
        return Stream.of(
            // path,                    a discipline that may write it, one that may not
            Arguments.of("/api/activity-logs", "ROLE_NURSE", "ROLE_CHEMIST"),
            Arguments.of("/api/allergies", "ROLE_NURSE", "ROLE_TECHNICIAN"),
            Arguments.of("/api/care-plan-items", "ROLE_THERAPIST", "ROLE_CHEMIST"),
            // DIAGNOSIS: doctor alone may write, so a nurse is the refused case rather than a lab role.
            Arguments.of("/api/conditions", "ROLE_DOCTOR", "ROLE_NURSE"),
            Arguments.of("/api/emergencies", "ROLE_CARER", "ROLE_PHARMACIST"),
            Arguments.of("/api/medications", "ROLE_PHARMACIST", "ROLE_TECHNICIAN"),
            Arguments.of("/api/reports", "ROLE_DOCTOR", "ROLE_NURSE"),
            Arguments.of("/api/stats", "ROLE_TECHNICIAN", "ROLE_PHARMACIST"),
            Arguments.of("/api/tasks", "ROLE_THERAPIST", "ROLE_CHEMIST"),
            Arguments.of("/api/visitations", "ROLE_CARER", "ROLE_PHARMACIST")
        );
    }

    @ParameterizedTest(name = "{0} refuses an archive with no reason")
    @MethodSource("clinicalRecords")
    @WithMockUser(username = "dr", authorities = { "ROLE_DOCTOR" })
    void anArchiveMustSayWhy(String path, String permitted, String refused) throws Exception {
        // Ahead of the not-found check on purpose: the reason is validated before the record is looked up, so this
        // holds without seeding anything. An archive with no reason is the delete this whole mechanism replaces.
        mockMvc
            .perform(post(path + "/{id}/archive", "any-id").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0} archiving follows the domain's write rule")
    @MethodSource("clinicalRecords")
    void archivingIsNeverWiderThanEditing(String path, String permitted, String refused) throws Exception {
        // The property that matters, and the reason the authority is derived from ClinicalDomain rather than named
        // per endpoint: a discipline that may not WRITE this kind of record must not be able to RETIRE one either.
        // 403 for the refused discipline; anything but 403 for the permitted one, since it gets past the scope check
        // and stops at the record not existing.
        mockMvc
            .perform(
                post(path + "/{id}/archive", "no-such-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"closed\"}")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .jwt()
                            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(refused))
                    )
            )
            .andExpect(status().isForbidden());

        int permittedStatus = mockMvc
            .perform(
                post(path + "/{id}/archive", "no-such-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"closed\"}")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .jwt()
                            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(permitted))
                    )
            )
            .andReturn()
            .getResponse()
            .getStatus();

        assertThat(permittedStatus).as("%s should let %s past the scope check", path, permitted).isNotEqualTo(403);
    }

    @ParameterizedTest(name = "{0} exposes both /archive and /unarchive")
    @MethodSource("clinicalRecords")
    @WithMockUser(username = "dr", authorities = { "ROLE_DOCTOR" })
    void theWayBackExistsToo(String path, String permitted, String refused) throws Exception {
        // Archiving without an unarchive is a delete with extra steps -- the one thing a clinician could do that
        // nobody could undo. 404 rather than 405: the route is mapped, the record simply is not there.
        mockMvc.perform(post(path + "/{id}/unarchive", "no-such-id")).andExpect(status().isNotFound());
    }
}
