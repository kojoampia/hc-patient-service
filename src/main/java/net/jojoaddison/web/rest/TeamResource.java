package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Team;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Team}.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamResource {

    private static final Logger LOG = LoggerFactory.getLogger(TeamResource.class);

    private static final String ENTITY_NAME = "patientMsTeam";

    @Value("${jhipster.clientApp.name:hcPatientService}")
    private String applicationName;

    private final TeamRepository teamRepository;

    public TeamResource(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /**
     * {@code POST  /teams} : Create a new team.
     *
     * @param team the team to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new team, or with status {@code 400 (Bad Request)} if the team has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Team> createTeam(@RequestBody Team team) throws URISyntaxException {
        LOG.debug("REST request to save Team : {}", team);
        if (team.getId() != null) {
            throw new BadRequestAlertException("A new team cannot already have an ID", ENTITY_NAME, "idexists");
        }
        team = teamRepository.save(team);
        return ResponseEntity
            .created(new URI("/api/teams/" + team.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, team.getId()))
            .body(team);
    }

    /**
     * {@code PUT  /teams/:id} : Updates an existing team.
     *
     * @param id the id of the team to save.
     * @param team the team to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated team,
     * or with status {@code 400 (Bad Request)} if the team is not valid,
     * or with status {@code 500 (Internal Server Error)} if the team couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Team> updateTeam(@PathVariable(value = "id", required = false) final String id, @RequestBody Team team)
        throws URISyntaxException {
        LOG.debug("REST request to update Team : {}, {}", id, team);
        if (team.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, team.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!teamRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        team = teamRepository.save(team);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, team.getId()))
            .body(team);
    }

    /**
     * {@code PATCH  /teams/:id} : Partial updates given fields of an existing team, field will ignore if it is null
     *
     * @param id the id of the team to save.
     * @param team the team to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated team,
     * or with status {@code 400 (Bad Request)} if the team is not valid,
     * or with status {@code 404 (Not Found)} if the team is not found,
     * or with status {@code 500 (Internal Server Error)} if the team couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Team> partialUpdateTeam(@PathVariable(value = "id", required = false) final String id, @RequestBody Team team)
        throws URISyntaxException {
        LOG.debug("REST request to partial update Team partially : {}, {}", id, team);
        if (team.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, team.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!teamRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Team> result = teamRepository
            .findById(team.getId())
            .map(existingTeam -> {
                updateIfPresent(existingTeam::setName, team.getName());
                updateIfPresent(existingTeam::setDescription, team.getDescription());
                updateIfPresent(existingTeam::setContact, team.getContact());

                return existingTeam;
            })
            .map(teamRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, team.getId()));
    }

    /**
     * {@code GET  /teams} : get all the Teams.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Teams in body.
     */
    @GetMapping("")
    public List<Team> getAllTeams() {
        LOG.debug("REST request to get all Teams");
        return teamRepository.findAll();
    }

    /**
     * {@code GET  /teams/:id} : get the "id" team.
     *
     * @param id the id of the team to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the team, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Team> getTeam(@PathVariable("id") String id) {
        LOG.debug("REST request to get Team : {}", id);
        Optional<Team> team = teamRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(team);
    }

    /**
     * {@code DELETE  /teams/:id} : delete the "id" team.
     *
     * @param id the id of the team to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Team : {}", id);
        teamRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
