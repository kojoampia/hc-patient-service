package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ClinicalCase;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.repository.ClinicalCaseRepository;
import net.jojoaddison.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * That the recommendation catalogue never names a clinical case.
 *
 * <p>{@code Recommendation} is reference data — a shared catalogue of labels — so its two GETs carry
 * no {@code PreAuthorize} and no {@code PatientScope}, exactly as {@code Team} and {@code DutyRoster}
 * do. That is the right posture for a catalogue and the wrong one for anything carrying a
 * {@code patientId}, and the entity carries the inverse side of a many-to-many with
 * {@code ClinicalCase}, which does.</p>
 *
 * <p>Nothing populates that side today, so a test asserting against the seeded data would pass
 * whatever the annotation said. <b>These tests populate it deliberately</b> and then assert the
 * field does not appear — which is the only way to tell a control from an empty collection.</p>
 *
 * <p>Both fail against {@code @JsonIgnoreProperties(value = { "recommendations" })}, which was what
 * this field carried until 2026-08-31: that annotation breaks the serialization cycle and leaves the
 * {@code ClinicalCase} objects themselves in the response, patient id and all.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class RecommendationDisclosureIT {

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private ClinicalCaseRepository clinicalCaseRepository;

    @Autowired
    private MockMvc restMockMvc;

    private Recommendation recommendation;

    @BeforeEach
    void setUp() {
        recommendationRepository.deleteAll();
        clinicalCaseRepository.deleteAll();

        ClinicalCase somebodyElsesCase = new ClinicalCase().patientId("patient-not-yours").title("A condition of theirs");
        clinicalCaseRepository.save(somebodyElsesCase);

        recommendation = new Recommendation().label("HbA1c blood test").category("diagnostic");
        recommendation.setClinicalCases(Set.of(somebodyElsesCase));
        recommendationRepository.save(recommendation);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_USER", "ROLE_PATIENT" })
    void theListDoesNotCarryTheCasesARecommendationIsAttachedTo() throws Exception {
        restMockMvc
            .perform(get("/api/recommendations?sort=id,desc"))
            .andExpect(status().isOk())
            // The catalogue itself stays readable — that is the point of it being reference data.
            .andExpect(jsonPath("$.[0].label").value("HbA1c blood test"))
            // And says nothing about whose cases it has been used on.
            .andExpect(jsonPath("$.[0].clinicalCases").doesNotExist())
            .andExpect(jsonPath("$..patientId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = { "ROLE_USER", "ROLE_PATIENT" })
    void fetchingOneRecommendationDoesNotCarryThemEither() throws Exception {
        restMockMvc
            .perform(get("/api/recommendations/{id}", recommendation.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("HbA1c blood test"))
            .andExpect(jsonPath("$.clinicalCases").doesNotExist())
            .andExpect(jsonPath("$..patientId").doesNotExist());
    }
}
