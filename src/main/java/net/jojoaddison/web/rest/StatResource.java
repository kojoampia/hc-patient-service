package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ArchiveSupport;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
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

    private final PatientScope patientScope;

    public StatResource(StatRepository statRepository, PatientScope patientScope) {
        this.statRepository = statRepository;
        this.patientScope = patientScope;
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
        patientScope.requireWrite(ClinicalDomain.OBSERVATION);
        if (stat.getId() != null) {
            throw new BadRequestAlertException("A new stat cannot already have an ID", ENTITY_NAME, "idexists");
        }
        stat.setPatientId(patientScope.requirePatientIdForWrite(stat.getPatientId()));
        // Provenance comes from the caller, never from the body — otherwise anyone could post a record
        // marked PROFESSIONAL and have it read as clinician-attested ever after.
        stat.setSource(patientScope.currentStatSource());
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        stat.setCreatedBy(AuditStamp.currentUser());
        stat.setCreatedDate(AuditStamp.today());
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
        patientScope.requireWrite(ClinicalDomain.OBSERVATION);
        if (stat.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, stat.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Stat existing = statRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        stat.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), stat.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        stat.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        stat.setCreatedBy(existing.getCreatedBy());
        stat.setCreatedDate(existing.getCreatedDate());

        // Archive state is carried from the stored record and never read from the payload. A PUT replaces

        // the document wholesale, so without this any caller who may edit a Stat could archive or

        // un-archive it by setting a field -- the /archive rule bypassed by the one verb nobody thought

        // about, and they would choose whose name went on it. Same defect ClinicalCase closed 2026-08-22.

        stat.setArchivedAt(existing.getArchivedAt());

        stat.setArchivedById(existing.getArchivedById());

        stat.setArchiveReason(existing.getArchiveReason());

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
        patientScope.requireWrite(ClinicalDomain.OBSERVATION);
        if (stat.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, stat.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Stat existing = statRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        stat.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), stat.getPatientId()));
        // Where a record came from does not change when somebody edits it. A clinician fixing a typo in a
        // patient's self-report has not turned it into a clinical finding.
        stat.setSource(existing.getSource());
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        stat.setCreatedBy(existing.getCreatedBy());
        stat.setCreatedDate(existing.getCreatedDate());

        Optional<Stat> result = statRepository
            .findById(stat.getId())
            .map(existingStat -> {
                if (stat.getPatientId() != null) {
                    existingStat.setPatientId(stat.getPatientId());
                }
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
                if (stat.getSecondaryValue() != null) {
                    existingStat.setSecondaryValue(stat.getSecondaryValue());
                }
                if (stat.getUnit() != null) {
                    existingStat.setUnit(stat.getUnit());
                }
                if (stat.getReferenceLow() != null) {
                    existingStat.setReferenceLow(stat.getReferenceLow());
                }
                if (stat.getReferenceHigh() != null) {
                    existingStat.setReferenceHigh(stat.getReferenceHigh());
                }
                if (stat.getFlag() != null) {
                    existingStat.setFlag(stat.getFlag());
                }
                if (stat.getNote() != null) {
                    existingStat.setNote(stat.getNote());
                }
                if (stat.getRecordedAt() != null) {
                    existingStat.setRecordedAt(stat.getRecordedAt());
                }
                if (stat.getSource() != null) {
                    existingStat.setSource(stat.getSource());
                }
                if (stat.getRecordedById() != null) {
                    existingStat.setRecordedById(stat.getRecordedById());
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
     * @param patientId when present, restricts the result to that patient's records.
     */
    /**
     * A page of readings.
     *
     * <p><b>Paginated later than its siblings, and deliberately last.</b> {@code Stat} is the collection in this
     * service with no natural ceiling — a patient's cases and reports are counted in dozens over years, their
     * vital-sign readings in hundreds over months, and telemetry would make that continuous. It was also the only
     * one still returning an unbounded {@code List}, which is the wrong way round.</p>
     *
     * <p>The order mattered because the clients read this. {@code PortalDataService} in both the dashboard and the
     * mobile app used to send no {@code size}, so Spring's default of 20 applied — and until those were fixed to
     * read every page, adding a {@code Pageable} here would have silently cut a patient's vitals panel from
     * whatever they had to twenty, with a 200 and no error. <b>Both clients must be deployed before this
     * ships</b>, not merely merged.</p>
     */
    @GetMapping("")
    public ResponseEntity<List<Stat>> getAllStats(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of Stats for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.OBSERVATION);
        Page<Stat> page = patientScope.findScopedPage(patientId, pageable, statRepository::findAll, statRepository::findByPatientId);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
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
        patientScope.requireRead(ClinicalDomain.OBSERVATION);
        Optional<Stat> stat = statRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(stat);
    }

    /**
     * {@code DELETE  /stats/:id} : delete the "id" stat.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the stat to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStat(@PathVariable("id") String id) {
        log.debug("REST request to delete Stat : {}", id);
        if (statRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        statRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST /api/stats/:id/archive} : retire a reading from the working lists.
     *
     * <p>The clinician's replacement for the delete that patient data does not allow. The record keeps every field
     * it had and its place in the patient's record; it stops appearing in the lists people work from.</p>
     *
     * <p><strong>The authority follows this entity's {@code ClinicalDomain}</strong> — OBSERVATION — so archiving is
     * never wider than editing. Deriving it rather than naming a role per endpoint is what stops the two drifting:
     * a discipline that may not write a reading must not be able to retire one either.</p>
     *
     * <p>{@code ROLE_ADMIN} is excluded deliberately, as it is on {@code ClinicalCase}, and that exclusion is why
     * this is a {@code requireWrite} call rather than only a {@code @PreAuthorize}: {@code PatientScope} returns
     * true for an administrator before it consults {@code ScopeOfPractice}, so the visibility check below is what
     * confines them to records they may already see.</p>
     *
     * @param body must carry a {@code reason}. An archive with no reason is the delete this exists to replace.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Stat> archiveStat(@PathVariable("id") String id, @RequestBody(required = false) Map<String, String> body) {
        log.debug("REST request to archive Stat : {}", id);
        patientScope.requireWrite(ClinicalDomain.OBSERVATION);
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException("An archive must say why", ENTITY_NAME, "reasonrequired");
        }
        // Visibility before existence, exactly as the read endpoints do: a caller who may not see a record must not
        // be able to learn that it exists by archiving it.
        if (statRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Stat archived = ArchiveSupport.archive(
            statRepository.findById(id),
            id,
            professionalId(),
            reason.trim(),
            ENTITY_NAME,
            "reading",
            statRepository::save
        );
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(archived);
    }

    /**
     * {@code POST /api/stats/:id/unarchive} : put a reading back.
     *
     * <p>Not optional. Without it archiving is a delete with extra steps — the one thing a clinician could do that
     * nobody could undo — and the mistake it invites is archiving the wrong row of a list.</p>
     */
    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Stat> unarchiveStat(@PathVariable("id") String id) {
        log.debug("REST request to unarchive Stat : {}", id);
        patientScope.requireWrite(ClinicalDomain.OBSERVATION);
        if (statRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Stat restored = ArchiveSupport.unarchive(statRepository.findById(id), id, ENTITY_NAME, "reading", statRepository::save);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, id)).body(restored);
    }

    /** The login of whoever acted, for the same reason {@code ClinicalCaseResource} gives: this service has no user management. */
    private String professionalId() {
        return SecurityUtils.getCurrentUserLogin().orElse(null);
    }
}
