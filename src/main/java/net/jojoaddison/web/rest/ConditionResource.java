package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Condition;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Condition}.
 */
@RestController
@RequestMapping("/api/conditions")
public class ConditionResource {

    private final Logger log = LoggerFactory.getLogger(ConditionResource.class);

    private static final String ENTITY_NAME = "patientMsCondition";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ConditionRepository conditionRepository;

    public ConditionResource(ConditionRepository conditionRepository) {
        this.conditionRepository = conditionRepository;
    }

    /**
     * {@code POST  /conditions} : Create a new condition.
     *
     * @param condition the condition to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new condition, or with status {@code 400 (Bad Request)} if the condition has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Condition> createCondition(@RequestBody Condition condition) throws URISyntaxException {
        log.debug("REST request to save Condition : {}", condition);
        if (condition.getId() != null) {
            throw new BadRequestAlertException("A new condition cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Condition result = conditionRepository.save(condition);
        return ResponseEntity
            .created(new URI("/api/conditions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /conditions/:id} : Updates an existing condition.
     *
     * @param id the id of the condition to save.
     * @param condition the condition to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated condition,
     * or with status {@code 400 (Bad Request)} if the condition is not valid,
     * or with status {@code 500 (Internal Server Error)} if the condition couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Condition> updateCondition(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Condition condition
    ) throws URISyntaxException {
        log.debug("REST request to update Condition : {}, {}", id, condition);
        if (condition.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, condition.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!conditionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Condition result = conditionRepository.save(condition);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, condition.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /conditions/:id} : Partial updates given fields of an existing condition, field will ignore if it is null
     *
     * @param id the id of the condition to save.
     * @param condition the condition to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated condition,
     * or with status {@code 400 (Bad Request)} if the condition is not valid,
     * or with status {@code 404 (Not Found)} if the condition is not found,
     * or with status {@code 500 (Internal Server Error)} if the condition couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Condition> partialUpdateCondition(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Condition condition
    ) throws URISyntaxException {
        log.debug("REST request to partial update Condition partially : {}, {}", id, condition);
        if (condition.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, condition.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!conditionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Condition> result = conditionRepository
            .findById(condition.getId())
            .map(existingCondition -> {
                if (condition.getName() != null) {
                    existingCondition.setName(condition.getName());
                }
                if (condition.getDescription() != null) {
                    existingCondition.setDescription(condition.getDescription());
                }
                if (condition.getPatientId() != null) {
                    existingCondition.setPatientId(condition.getPatientId());
                }
                if (condition.getCreatedDate() != null) {
                    existingCondition.setCreatedDate(condition.getCreatedDate());
                }
                if (condition.getModifiedDate() != null) {
                    existingCondition.setModifiedDate(condition.getModifiedDate());
                }
                if (condition.getCreatedBy() != null) {
                    existingCondition.setCreatedBy(condition.getCreatedBy());
                }
                if (condition.getModifiedBy() != null) {
                    existingCondition.setModifiedBy(condition.getModifiedBy());
                }

                return existingCondition;
            })
            .map(conditionRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, condition.getId())
        );
    }

    /**
     * {@code GET  /conditions} : get all the conditions.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of conditions in body.
     */
    @GetMapping("")
    public List<Condition> getAllConditions() {
        log.debug("REST request to get all Conditions");
        return conditionRepository.findAll();
    }

    /**
     * {@code GET  /conditions/:id} : get the "id" condition.
     *
     * @param id the id of the condition to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the condition, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Condition> getCondition(@PathVariable("id") String id) {
        log.debug("REST request to get Condition : {}", id);
        Optional<Condition> condition = conditionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(condition);
    }

    /**
     * {@code DELETE  /conditions/:id} : delete the "id" condition.
     *
     * @param id the id of the condition to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCondition(@PathVariable("id") String id) {
        log.debug("REST request to delete Condition : {}", id);
        conditionRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
