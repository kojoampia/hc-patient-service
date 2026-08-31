package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PaymentOption;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.PaymentOptionRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Retiring a payment option.
 *
 * <p>The first of the five administrative resources to gain archiving, and the only one chosen for it: it is the
 * only one with no existing field that could stand in for retirement. {@code Membership} has {@code status},
 * {@code PersonalDocument} has {@code expiresOn}, and ending a {@code Profile} already has a verb in
 * {@code DeletionRequest}.</p>
 *
 * <p><b>Runs as an ordinary patient, and that is the assertion.</b> Every other archive endpoint in this service
 * requires a clinical discipline, because the thing being retired is clinical. A payment option is not: it is
 * billing housekeeping on somebody's own record, and requiring an administrator would make the feature useless to
 * the only person who routinely needs it. If somebody later "tightens" this to {@code ROLE_ADMIN} for consistency
 * with the clinical endpoints, these tests are what should stop them.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class PaymentOptionArchiveIT {

    private static final String API = "/api/payment-options";
    private static final String PATIENT_ID = "patient-kojo";
    private static final String EMAIL = "kojo@example.test";

    @Autowired
    private PaymentOptionRepository paymentOptionRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MockMvc restMockMvc;

    private PaymentOption option;

    @BeforeEach
    void setUp() {
        paymentOptionRepository.deleteAll();
        profileRepository.deleteAll();

        Profile profile = new Profile();
        profile.setId(PATIENT_ID);
        profile.setEmail(EMAIL);
        profileRepository.save(profile);

        option = new PaymentOption();
        option.setUserID(PATIENT_ID);
        option.setType("VISA");
        paymentOptionRepository.save(option);
    }

    private static String reason(String why) {
        return "{\"reason\":\"" + why + "\"}";
    }

    /**
     * A patient, as a JWT rather than a mock user.
     *
     * <p>{@code @WithMockUser} cannot express this, and the first version of these tests failed because of it: the
     * identity {@code PatientScope} resolves on lives in the token's {@code email} claim, and a mock user carries no
     * JWT and therefore no claim. The scope then resolves to nothing, every request 404s, and the failure reads as a
     * broken endpoint rather than a broken fixture. {@code PatientScopeIT} says the same at its top, which is where
     * this pattern comes from.</p>
     */
    private static RequestPostProcessor kojo() {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, EMAIL))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }

    @Test
    void aPatientCanRetireTheirOwnExpiredCard() throws Exception {
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId())
                    .with(kojo())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reason("Card expired"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archiveReason").value("Card expired"));

        assertThat(paymentOptionRepository.findById(option.getId()).orElseThrow().isArchived()).isTrue();
    }

    @Test
    void anArchiveMustSayWhy() throws Exception {
        // An archive with no reason is the delete this replaces.
        restMockMvc
            .perform(post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        assertThat(paymentOptionRepository.findById(option.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void aRetiredCardLeavesTheList() throws Exception {
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("Expired"))
            )
            .andExpect(status().isOk());

        // The half of archiving a client actually sees: the retired card stops appearing beside the live one.
        restMockMvc.perform(get(API).with(kojo())).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void butItIsStillThereWhenAskedFor() throws Exception {
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("Expired"))
            )
            .andExpect(status().isOk());

        restMockMvc
            .perform(get(API + "?includeArchived=true").with(kojo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists());

        // Excluded from the list, not hidden: a link to it keeps working without un-archiving anything.
        restMockMvc.perform(get(API + "/{id}", option.getId()).with(kojo())).andExpect(status().isOk());
    }

    @Test
    void archivingTwiceIsRefused() throws Exception {
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("One"))
            )
            .andExpect(status().isOk());

        // Otherwise the second call would overwrite who retired it and why, losing the record of the first.
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("Two"))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void aCardRetiredByMistakeCanComeBack() throws Exception {
        restMockMvc
            .perform(
                post(API + "/{id}/archive", option.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("Mistake"))
            )
            .andExpect(status().isOk());

        // A one-way archive is a delete with extra steps.
        restMockMvc.perform(post(API + "/{id}/unarchive", option.getId()).with(kojo())).andExpect(status().isOk());

        assertThat(paymentOptionRepository.findById(option.getId()).orElseThrow().isArchived()).isFalse();
        restMockMvc.perform(get(API).with(kojo())).andExpect(jsonPath("$[0].id").value(option.getId()));
    }

    @Test
    void somebodyElsesCardCannotBeRetired() throws Exception {
        PaymentOption theirs = new PaymentOption();
        theirs.setUserID("patient-someone-else");
        theirs.setType("MTN-MOMO");
        paymentOptionRepository.save(theirs);

        // 404 rather than 403, deliberately: visibility is checked before existence, so archiving cannot be used to
        // learn that a record exists.
        restMockMvc
            .perform(
                post(API + "/{id}/archive", theirs.getId()).with(kojo()).contentType(MediaType.APPLICATION_JSON).content(reason("Nope"))
            )
            .andExpect(status().isNotFound());

        assertThat(paymentOptionRepository.findById(theirs.getId()).orElseThrow().isArchived()).isFalse();
    }
}
