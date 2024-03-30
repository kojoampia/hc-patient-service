package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Stat}.
 */
@RestController
@RequestMapping("/api/stats")
public class StatResource {

    private final Logger log = LoggerFactory.getLogger(StatResource.class);

    private static final String ENTITY_NAME = "patientMsStat";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final StatRepository statRepository;

    public StatResource(StatRepository statRepository) {
        this.statRepository = statRepository;
    }

    /**
     * {@code POST  /stats} : Create a new stat.
     *
     * @param stat the stat to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new stat, or with status {@code 400 (Bad Request)} if the stat has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Stat> createStat(@RequestBody Stat stat) throws URISyntaxException {
        log.debug("REST request to save Stat : {}", stat);
        if (stat.getId() != null) {
            throw new BadRequestAlertException("A new stat cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Stat result = statRepository.save(stat);
        return ResponseEntity
            .created(new URI("/api/stats/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /stats/:id} : Updates an existing stat.
     *
     * @param id the id of the stat to save.
     * @param stat the stat to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated stat,
     * or with status {@code 400 (Bad Request)} if the stat is not valid,
     * or with status {@code 500 (Internal Server Error)} if the stat couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Stat> updateStat(@PathVariable(value = "id", required = false) final String id, @RequestBody Stat stat)
        throws URISyntaxException {
        log.debug("REST request to update Stat : {}, {}", id, stat);
        if (stat.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, stat.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!statRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Stat result = statRepository.save(stat);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, stat.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /stats/:id} : Partial updates given fields of an existing stat, field will ignore if it is null
     *
     * @param id the id of the stat to save.
     * @param stat the stat to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated stat,
     * or with status {@code 400 (Bad Request)} if the stat is not valid,
     * or with status {@code 404 (Not Found)} if the stat is not found,
     * or with status {@code 500 (Internal Server Error)} if the stat couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Stat> partialUpdateStat(@PathVariable(value = "id", required = false) final String id, @RequestBody Stat stat)
        throws URISyntaxException {
        log.debug("REST request to partial update Stat partially : {}, {}", id, stat);
        if (stat.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, stat.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!statRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Stat> result = statRepository
            .findById(stat.getId())
            .map(existingStat -> {
                if (stat.getType() != null) {
                    existingStat.setType(stat.getType());
                }
                if (stat.getName() != null) {
                    existingStat.setName(stat.getName());
                }
                if (stat.getDescription() != null) {
                    existingStat.setDescription(stat.getDescription());
                }
                if (stat.getValue() != null) {
                    existingStat.setValue(stat.getValue());
                }
                if (stat.getNote() != null) {
                    existingStat.setNote(stat.getNote());
                }
                if (stat.getPatientId() != null) {
                    existingStat.setPatientId(stat.getPatientId());
                }
                if (stat.getCreatedDate() != null) {
                    existingStat.setCreatedDate(stat.getCreatedDate());
                }
                if (stat.getCreatedBy() != null) {
                    existingStat.setCreatedBy(stat.getCreatedBy());
                }

                return existingStat;
            })
            .map(statRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, stat.getId()));
    }

    /**
     * {@code GET  /stats} : get all the stats.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of stats in body.
     */
    @GetMapping("")
    public List<Stat> getAllStats() {
        log.debug("REST request to get all Stats");
        return statRepository.findAll();
    }

    /**
     * {@code GET  /stats/:id} : get the "id" stat.
     *
     * @param id the id of the stat to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the stat, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Stat> getStat(@PathVariable("id") String id) {
        log.debug("REST request to get Stat : {}", id);
        Optional<Stat> stat = statRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(stat);
    }

    /**
     * {@code DELETE  /stats/:id} : delete the "id" stat.
     *
     * @param id the id of the stat to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStat(@PathVariable("id") String id) {
        log.debug("REST request to delete Stat : {}", id);
        statRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
