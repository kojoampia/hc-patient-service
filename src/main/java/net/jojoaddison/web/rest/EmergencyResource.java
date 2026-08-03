package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Emergency;
import net.jojoaddison.repository.EmergencyRepository;
import net.jojoaddison.service.EmergencyService;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Emergency}.
 */
@RestController
@RequestMapping("/api/emergencies")
public class EmergencyResource {

    private final Logger log = LoggerFactory.getLogger(EmergencyResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceEmergency";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmergencyService emergencyService;

    private final EmergencyRepository emergencyRepository;

    public EmergencyResource(EmergencyService emergencyService, EmergencyRepository emergencyRepository) {
        this.emergencyService = emergencyService;
        this.emergencyRepository = emergencyRepository;
    }

    /**
     * {@code POST  /emergencies} : Create a new emergency.
     *
     * @param emergency the emergency to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new emergency, or with status {@code 400 (Bad Request)} if the emergency has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Emergency> createEmergency(@RequestBody Emergency emergency) throws URISyntaxException {
        log.debug("REST request to save Emergency : {}", emergency);
        if (emergency.getId() != null) {
            throw new BadRequestAlertException("A new emergency cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Emergency result = emergencyService.save(emergency);
        return ResponseEntity
            .created(new URI("/api/emergencies/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /emergencies/:id} : Updates an existing emergency.
     *
     * @param id the id of the emergency to save.
     * @param emergency the emergency to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated emergency,
     * or with status {@code 400 (Bad Request)} if the emergency is not valid,
     * or with status {@code 500 (Internal Server Error)} if the emergency couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Emergency> updateEmergency(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Emergency emergency
    ) throws URISyntaxException {
        log.debug("REST request to update Emergency : {}, {}", id, emergency);
        if (emergency.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, emergency.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!emergencyRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Emergency result = emergencyService.update(emergency);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, emergency.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /emergencies/:id} : Partial updates given fields of an existing emergency, field will ignore if it is null
     *
     * @param id the id of the emergency to save.
     * @param emergency the emergency to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated emergency,
     * or with status {@code 400 (Bad Request)} if the emergency is not valid,
     * or with status {@code 404 (Not Found)} if the emergency is not found,
     * or with status {@code 500 (Internal Server Error)} if the emergency couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Emergency> partialUpdateEmergency(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Emergency emergency
    ) throws URISyntaxException {
        log.debug("REST request to partial update Emergency partially : {}, {}", id, emergency);
        if (emergency.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, emergency.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!emergencyRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Emergency> result = emergencyService.partialUpdate(emergency);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, emergency.getId())
        );
    }

    /**
     * {@code GET  /emergencies} : get all the emergencies.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of emergencies in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<Emergency> getAllEmergencies(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all Emergencys for patient {}", patientId);
        return patientId == null ? emergencyRepository.findAll() : emergencyRepository.findByPatientId(patientId);
    }

    /**
     * {@code GET  /emergencies/:id} : get the "id" emergency.
     *
     * @param id the id of the emergency to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the emergency, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Emergency> getEmergency(@PathVariable("id") String id) {
        log.debug("REST request to get Emergency : {}", id);
        Optional<Emergency> emergency = emergencyService.findOne(id);
        return ResponseUtil.wrapOrNotFound(emergency);
    }

    /**
     * {@code DELETE  /emergencies/:id} : delete the "id" emergency.
     *
     * @param id the id of the emergency to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmergency(@PathVariable("id") String id) {
        log.debug("REST request to delete Emergency : {}", id);
        emergencyService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
