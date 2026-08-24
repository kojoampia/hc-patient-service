package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Shift;
import net.jojoaddison.repository.ShiftRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.Shift}.
 */
@RestController
@RequestMapping("/api/shifts")
public class ShiftResource {

    private final Logger log = LoggerFactory.getLogger(ShiftResource.class);

    private static final String ENTITY_NAME = "hcPatientServiceShift";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ShiftRepository shiftRepository;

    public ShiftResource(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /**
     * {@code POST  /shifts} : Create a new shift.
     *
     * @param shift the shift to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new shift, or with status {@code 400 (Bad Request)} if the shift has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    // Staff reference data: readable by any authenticated caller, writable only by staff — the same
    // rule Professional and Team carry, and for the same reason. Who is on duty is not something a
    // patient gets to rewrite.
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Shift> createShift(@RequestBody Shift shift) throws URISyntaxException {
        log.debug("REST request to save Shift : {}", shift);
        if (shift.getId() != null) {
            throw new BadRequestAlertException("A new shift cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Shift result = shiftRepository.save(shift);
        return ResponseEntity
            .created(new URI("/api/shifts/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /shifts/:id} : Updates an existing shift.
     *
     * @param id the id of the shift to save.
     * @param shift the shift to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated shift,
     * or with status {@code 400 (Bad Request)} if the shift is not valid,
     * or with status {@code 500 (Internal Server Error)} if the shift couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Shift> updateShift(@PathVariable(value = "id", required = false) final String id, @RequestBody Shift shift)
        throws URISyntaxException {
        log.debug("REST request to update Shift : {}, {}", id, shift);
        if (shift.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, shift.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!shiftRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Shift result = shiftRepository.save(shift);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, shift.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /shifts/:id} : Partial updates given fields of an existing shift, field will ignore if it is null
     *
     * @param id the id of the shift to save.
     * @param shift the shift to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated shift,
     * or with status {@code 400 (Bad Request)} if the shift is not valid,
     * or with status {@code 404 (Not Found)} if the shift is not found,
     * or with status {@code 500 (Internal Server Error)} if the shift couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Shift> partialUpdateShift(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Shift shift
    ) throws URISyntaxException {
        log.debug("REST request to partial update Shift partially : {}, {}", id, shift);
        if (shift.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, shift.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!shiftRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Shift> result = shiftRepository
            .findById(shift.getId())
            .map(existingShift -> {
                if (shift.getRosterId() != null) {
                    existingShift.setRosterId(shift.getRosterId());
                }
                if (shift.getProfessionalId() != null) {
                    existingShift.setProfessionalId(shift.getProfessionalId());
                }
                if (shift.getStartsAt() != null) {
                    existingShift.setStartsAt(shift.getStartsAt());
                }
                if (shift.getEndsAt() != null) {
                    existingShift.setEndsAt(shift.getEndsAt());
                }
                if (shift.getStatus() != null) {
                    existingShift.setStatus(shift.getStatus());
                }
                if (shift.getNotes() != null) {
                    existingShift.setNotes(shift.getNotes());
                }
                if (shift.getCreatedDate() != null) {
                    existingShift.setCreatedDate(shift.getCreatedDate());
                }
                if (shift.getModifiedDate() != null) {
                    existingShift.setModifiedDate(shift.getModifiedDate());
                }
                if (shift.getCreatedBy() != null) {
                    existingShift.setCreatedBy(shift.getCreatedBy());
                }
                if (shift.getModifiedBy() != null) {
                    existingShift.setModifiedBy(shift.getModifiedBy());
                }

                return existingShift;
            })
            .map(shiftRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, shift.getId()));
    }

    /**
     * {@code GET  /shifts} : get all the shifts.
     *
     * @param rosterId when present, restricts the result to that roster's shifts.
     * @param professionalId when present, restricts the result to that professional's shifts.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of shifts in body.
     */
    @GetMapping("")
    public List<Shift> getAllShifts(
        @RequestParam(required = false) String rosterId,
        @RequestParam(required = false) String professionalId
    ) {
        log.debug("REST request to get all Shifts for roster {} and professional {}", rosterId, professionalId);
        if (rosterId != null) {
            List<Shift> shifts = shiftRepository.findByRosterId(rosterId);
            if (professionalId != null) {
                return shifts.stream().filter(shift -> professionalId.equals(shift.getProfessionalId())).toList();
            }
            return shifts;
        }
        if (professionalId != null) {
            return shiftRepository.findByProfessionalId(professionalId);
        }
        return shiftRepository.findAll();
    }

    /**
     * {@code GET  /shifts/:id} : get the "id" shift.
     *
     * @param id the id of the shift to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the shift, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Shift> getShift(@PathVariable("id") String id) {
        log.debug("REST request to get Shift : {}", id);
        Optional<Shift> shift = shiftRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(shift);
    }

    /**
     * {@code DELETE  /shifts/:id} : delete the "id" shift.
     *
     * @param id the id of the shift to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', " + AuthoritiesConstants.CLINICAL_AUTHORITIES + ")")
    public ResponseEntity<Void> deleteShift(@PathVariable("id") String id) {
        log.debug("REST request to delete Shift : {}", id);
        shiftRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
