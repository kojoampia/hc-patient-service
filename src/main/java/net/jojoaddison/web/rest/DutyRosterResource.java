package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.DutyRoster}.
 */
@RestController
@RequestMapping("/api/duty-rosters")
public class DutyRosterResource {

    private final Logger log = LoggerFactory.getLogger(DutyRosterResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceDutyRoster";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DutyRosterRepository dutyRosterRepository;

    public DutyRosterResource(DutyRosterRepository dutyRosterRepository) {
        this.dutyRosterRepository = dutyRosterRepository;
    }

    /**
     * {@code POST  /duty-rosters} : Create a new dutyRoster.
     *
     * @param dutyRoster the dutyRoster to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dutyRoster, or with status {@code 400 (Bad Request)} if the dutyRoster has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    // Staff reference data: readable by any authenticated caller, writable only by staff — the same
    // rule Professional and Team carry, and for the same reason. Who is on duty is not something a
    // patient gets to rewrite.
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<DutyRoster> createDutyRoster(@RequestBody DutyRoster dutyRoster) throws URISyntaxException {
        log.debug("REST request to save DutyRoster : {}", dutyRoster);
        if (dutyRoster.getId() != null) {
            throw new BadRequestAlertException("A new dutyRoster cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DutyRoster result = dutyRosterRepository.save(dutyRoster);
        return ResponseEntity
            .created(new URI("/api/duty-rosters/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /duty-rosters/:id} : Updates an existing dutyRoster.
     *
     * @param id the id of the dutyRoster to save.
     * @param dutyRoster the dutyRoster to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dutyRoster,
     * or with status {@code 400 (Bad Request)} if the dutyRoster is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dutyRoster couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<DutyRoster> updateDutyRoster(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DutyRoster dutyRoster
    ) throws URISyntaxException {
        log.debug("REST request to update DutyRoster : {}, {}", id, dutyRoster);
        if (dutyRoster.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dutyRoster.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dutyRosterRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        DutyRoster result = dutyRosterRepository.save(dutyRoster);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dutyRoster.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /duty-rosters/:id} : Partial updates given fields of an existing dutyRoster, field will ignore if it is null
     *
     * @param id the id of the dutyRoster to save.
     * @param dutyRoster the dutyRoster to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dutyRoster,
     * or with status {@code 400 (Bad Request)} if the dutyRoster is not valid,
     * or with status {@code 404 (Not Found)} if the dutyRoster is not found,
     * or with status {@code 500 (Internal Server Error)} if the dutyRoster couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<DutyRoster> partialUpdateDutyRoster(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody DutyRoster dutyRoster
    ) throws URISyntaxException {
        log.debug("REST request to partial update DutyRoster partially : {}, {}", id, dutyRoster);
        if (dutyRoster.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, dutyRoster.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!dutyRosterRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<DutyRoster> result = dutyRosterRepository
            .findById(dutyRoster.getId())
            .map(existingDutyRoster -> {
                if (dutyRoster.getName() != null) {
                    existingDutyRoster.setName(dutyRoster.getName());
                }
                if (dutyRoster.getDescription() != null) {
                    existingDutyRoster.setDescription(dutyRoster.getDescription());
                }
                if (dutyRoster.getLocation() != null) {
                    existingDutyRoster.setLocation(dutyRoster.getLocation());
                }
                // An empty set is indistinguishable from "not supplied" once the body is deserialised —
                // the field defaults to an empty set — so a patch can add subscribers but never clear
                // them. Use PUT to empty a roster.
                if (!dutyRoster.getSubscribedProfessionalIds().isEmpty()) {
                    existingDutyRoster.setSubscribedProfessionalIds(dutyRoster.getSubscribedProfessionalIds());
                }
                if (dutyRoster.getCreatedDate() != null) {
                    existingDutyRoster.setCreatedDate(dutyRoster.getCreatedDate());
                }
                if (dutyRoster.getModifiedDate() != null) {
                    existingDutyRoster.setModifiedDate(dutyRoster.getModifiedDate());
                }
                if (dutyRoster.getCreatedBy() != null) {
                    existingDutyRoster.setCreatedBy(dutyRoster.getCreatedBy());
                }
                if (dutyRoster.getModifiedBy() != null) {
                    existingDutyRoster.setModifiedBy(dutyRoster.getModifiedBy());
                }

                return existingDutyRoster;
            })
            .map(dutyRosterRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, dutyRoster.getId())
        );
    }

    /**
     * {@code GET  /duty-rosters} : get all the dutyRosters.
     *
     * @param professionalId when present, restricts the result to the rosters that professional subscribes to.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of dutyRosters in body.
     */
    @GetMapping("")
    public List<DutyRoster> getAllDutyRosters(@RequestParam(required = false) String professionalId) {
        log.debug("REST request to get all DutyRosters for professional {}", professionalId);
        if (professionalId != null) {
            return dutyRosterRepository.findBySubscribedProfessionalIdsContains(professionalId);
        }
        return dutyRosterRepository.findAll();
    }

    /**
     * {@code GET  /duty-rosters/:id} : get the "id" dutyRoster.
     *
     * @param id the id of the dutyRoster to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dutyRoster, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DutyRoster> getDutyRoster(@PathVariable("id") String id) {
        log.debug("REST request to get DutyRoster : {}", id);
        Optional<DutyRoster> dutyRoster = dutyRosterRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(dutyRoster);
    }

    /**
     * {@code DELETE  /duty-rosters/:id} : delete the "id" dutyRoster.
     *
     * @param id the id of the dutyRoster to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.PROFESSIONAL + "')")
    public ResponseEntity<Void> deleteDutyRoster(@PathVariable("id") String id) {
        log.debug("REST request to delete DutyRoster : {}", id);
        dutyRosterRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
