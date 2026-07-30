package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Recommendation;
import net.jojoaddison.repository.RecommendationRepository;
import net.jojoaddison.service.RecommendationService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Recommendation}.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationResource {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceRecommendation";

    @Value("${jhipster.clientApp.name:hcPatientService}")
    private String applicationName;

    private final RecommendationService recommendationService;

    private final RecommendationRepository recommendationRepository;

    public RecommendationResource(RecommendationService recommendationService, RecommendationRepository recommendationRepository) {
        this.recommendationService = recommendationService;
        this.recommendationRepository = recommendationRepository;
    }

    /**
     * {@code POST  /recommendations} : Create a new recommendation.
     *
     * @param recommendation the recommendation to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new recommendation, or with status {@code 400 (Bad Request)} if the recommendation has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Recommendation> createRecommendation(@RequestBody Recommendation recommendation) throws URISyntaxException {
        LOG.debug("REST request to save Recommendation : {}", recommendation);
        if (recommendation.getId() != null) {
            throw new BadRequestAlertException("A new recommendation cannot already have an ID", ENTITY_NAME, "idexists");
        }
        recommendation = recommendationService.save(recommendation);
        return ResponseEntity
            .created(new URI("/api/recommendations/" + recommendation.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, recommendation.getId()))
            .body(recommendation);
    }

    /**
     * {@code PUT  /recommendations/:id} : Updates an existing recommendation.
     *
     * @param id the id of the recommendation to save.
     * @param recommendation the recommendation to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated recommendation,
     * or with status {@code 400 (Bad Request)} if the recommendation is not valid,
     * or with status {@code 500 (Internal Server Error)} if the recommendation couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Recommendation> updateRecommendation(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Recommendation recommendation
    ) throws URISyntaxException {
        LOG.debug("REST request to update Recommendation : {}, {}", id, recommendation);
        if (recommendation.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, recommendation.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!recommendationRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        recommendation = recommendationService.update(recommendation);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, recommendation.getId()))
            .body(recommendation);
    }

    /**
     * {@code PATCH  /recommendations/:id} : Partial updates given fields of an existing recommendation, field will ignore if it is null
     *
     * @param id the id of the recommendation to save.
     * @param recommendation the recommendation to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated recommendation,
     * or with status {@code 400 (Bad Request)} if the recommendation is not valid,
     * or with status {@code 404 (Not Found)} if the recommendation is not found,
     * or with status {@code 500 (Internal Server Error)} if the recommendation couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Recommendation> partialUpdateRecommendation(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Recommendation recommendation
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Recommendation partially : {}, {}", id, recommendation);
        if (recommendation.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, recommendation.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!recommendationRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Recommendation> result = recommendationService.partialUpdate(recommendation);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, recommendation.getId())
        );
    }

    /**
     * {@code GET  /recommendations} : get all the Med Cases.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Med Cases in body.
     */
    @GetMapping("")
    public List<Recommendation> getAllRecommendations() {
        LOG.debug("REST request to get all Recommendations");
        return recommendationService.findAll();
    }

    /**
     * {@code GET  /recommendations/:id} : get the "id" recommendation.
     *
     * @param id the id of the recommendation to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the recommendation, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Recommendation> getRecommendation(@PathVariable("id") String id) {
        LOG.debug("REST request to get Recommendation : {}", id);
        Optional<Recommendation> recommendation = recommendationService.findOne(id);
        return ResponseUtil.wrapOrNotFound(recommendation);
    }

    /**
     * {@code DELETE  /recommendations/:id} : delete the "id" recommendation.
     *
     * @param id the id of the recommendation to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecommendation(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Recommendation : {}", id);
        recommendationService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
