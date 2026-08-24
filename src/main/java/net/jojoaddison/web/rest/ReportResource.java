package net.jojoaddison.web.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.Report;
import net.jojoaddison.repository.ReportRepository;
import net.jojoaddison.security.AuditStamp;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ClinicalDomain;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.ReportFileService;
import net.jojoaddison.service.UnsupportedReportFileException;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Report}.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportResource {

    private final Logger log = LoggerFactory.getLogger(ReportResource.class);

    private static final String ENTITY_NAME = "patientMsReport";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ReportRepository reportRepository;

    private final PatientScope patientScope;

    private final ReportFileService reportFiles;

    public ReportResource(ReportRepository reportRepository, PatientScope patientScope, ReportFileService reportFiles) {
        this.reportRepository = reportRepository;
        this.patientScope = patientScope;
        this.reportFiles = reportFiles;
    }

    /**
     * {@code POST  /reports} : Create a new report.
     *
     * @param report the report to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new report, or with status {@code 400 (Bad Request)} if the report has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Report> createReport(@RequestBody Report report) throws URISyntaxException {
        log.debug("REST request to save Report : {}", report);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (report.getId() != null) {
            throw new BadRequestAlertException("A new report cannot already have an ID", ENTITY_NAME, "idexists");
        }
        report.setPatientId(patientScope.requirePatientIdForWrite(report.getPatientId()));
        // Audit identity comes from the token, never from the body — see AuditStamp. A caller must not be
        // able to attribute a record to somebody else or backdate it.
        report.setCreatedBy(AuditStamp.currentUser());
        report.setCreatedDate(AuditStamp.today());
        report.setModifiedBy(AuditStamp.currentUser());
        report.setModifiedDate(AuditStamp.today());
        Report result = reportRepository.save(report);
        return ResponseEntity
            .created(new URI("/api/reports/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /reports/:id} : Updates an existing report.
     *
     * @param id the id of the report to save.
     * @param report the report to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated report,
     * or with status {@code 400 (Bad Request)} if the report is not valid,
     * or with status {@code 500 (Internal Server Error)} if the report couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Report> updateReport(@PathVariable(value = "id", required = false) final String id, @RequestBody Report report)
        throws URISyntaxException {
        log.debug("REST request to update Report : {}, {}", id, report);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (report.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, report.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Report existing = reportRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        report.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), report.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        report.setCreatedBy(existing.getCreatedBy());
        report.setCreatedDate(existing.getCreatedDate());
        report.setModifiedBy(AuditStamp.currentUser());
        report.setModifiedDate(AuditStamp.today());

        Report result = reportRepository.save(report);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, report.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /reports/:id} : Partial updates given fields of an existing report, field will ignore if it is null
     *
     * @param id the id of the report to save.
     * @param report the report to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated report,
     * or with status {@code 400 (Bad Request)} if the report is not valid,
     * or with status {@code 404 (Not Found)} if the report is not found,
     * or with status {@code 500 (Internal Server Error)} if the report couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Report> partialUpdateReport(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody Report report
    ) throws URISyntaxException {
        log.debug("REST request to partial update Report partially : {}, {}", id, report);
        patientScope.requireWrite(ClinicalDomain.DIAGNOSIS);
        if (report.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, report.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        Report existing = reportRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        report.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), report.getPatientId()));
        // Creation facts are the stored ones; a caller cannot rewrite who created a record or when.
        report.setCreatedBy(existing.getCreatedBy());
        report.setCreatedDate(existing.getCreatedDate());
        report.setModifiedBy(AuditStamp.currentUser());
        report.setModifiedDate(AuditStamp.today());

        Optional<Report> result = reportRepository
            .findById(report.getId())
            .map(existingReport -> {
                if (report.getCategory() != null) {
                    existingReport.setCategory(report.getCategory());
                }
                if (report.getDescription() != null) {
                    existingReport.setDescription(report.getDescription());
                }
                if (report.getSummary() != null) {
                    existingReport.setSummary(report.getSummary());
                }
                if (report.getName() != null) {
                    existingReport.setName(report.getName());
                }
                if (report.getUrl() != null) {
                    existingReport.setUrl(report.getUrl());
                }
                if (report.getPatientId() != null) {
                    existingReport.setPatientId(report.getPatientId());
                }
                if (report.getCaseId() != null) {
                    existingReport.setCaseId(report.getCaseId());
                }
                if (report.getAuthorId() != null) {
                    existingReport.setAuthorId(report.getAuthorId());
                }
                if (report.getReportDate() != null) {
                    existingReport.setReportDate(report.getReportDate());
                }
                if (report.getCreatedDate() != null) {
                    existingReport.setCreatedDate(report.getCreatedDate());
                }
                if (report.getModifiedDate() != null) {
                    existingReport.setModifiedDate(report.getModifiedDate());
                }
                if (report.getCreatedBy() != null) {
                    existingReport.setCreatedBy(report.getCreatedBy());
                }
                if (report.getModifiedBy() != null) {
                    existingReport.setModifiedBy(report.getModifiedBy());
                }

                return existingReport;
            })
            .map(reportRepository::save);

        return ResponseUtil.wrapOrNotFound(result, HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, report.getId()));
    }

    /**
     * {@code GET  /reports} : get all the reports.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of reports in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public ResponseEntity<List<Report>> getAllReports(
        @RequestParam(required = false) String patientId,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        log.debug("REST request to get a page of Reports for patient {}", patientId);
        patientScope.requireRead(ClinicalDomain.DIAGNOSIS);
        Page<Report> page = patientScope.findScopedPage(patientId, pageable, reportRepository::findAll, reportRepository::findByPatientId);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /reports/:id} : get the "id" report.
     *
     * @param id the id of the report to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the report, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Report> getReport(@PathVariable("id") String id) {
        log.debug("REST request to get Report : {}", id);
        patientScope.requireRead(ClinicalDomain.DIAGNOSIS);
        Optional<Report> report = reportRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(report);
    }

    /**
     * {@code DELETE  /reports/:id} : delete the "id" report.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the report to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable("id") String id) {
        log.debug("REST request to delete Report : {}", id);
        if (reportRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        reportRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }

    /**
     * {@code POST  /reports/:id/file} : attach a file to the "id" report.
     *
     * <p>The report document is created first and the file is attached to it, rather than the two arriving together.
     * That keeps this endpoint out of the business of building a Report from form fields, and it means an upload that
     * fails leaves a report the patient can retry against instead of nothing at all.</p>
     *
     * <p>Scoped exactly like every other endpoint here: a file may be attached only to a report the caller can
     * already see, so a patient cannot write into somebody else's record by guessing an id.</p>
     *
     * @param id the report to attach to.
     * @param file the uploaded file — PDF, JPEG, PNG or HEIC, up to 10 MB, decided from its bytes.
     * @return the updated report, whose {@code url} now points at the download endpoint.
     */
    @PostMapping("/{id}/file")
    public ResponseEntity<Report> uploadReportFile(@PathVariable("id") String id, @RequestParam("file") MultipartFile file) {
        log.debug("REST request to attach a file to Report : {}", id);
        Report report = reportRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).orElse(null);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            reportFiles.store(report, file);
        } catch (UnsupportedReportFileException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "filerejected");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Relative on purpose: the browser reaches this service through the gateway, and the gateway's own host is
        // the only one that resolves for it. An absolute URL built from this container's request would point at a
        // host nobody outside the compose network can reach.
        report.setUrl("api/reports/" + report.getId() + "/file");
        Report saved = reportRepository.save(report);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, saved.getId()))
            .body(saved);
    }

    /**
     * {@code GET  /reports/:id/file} : download the file attached to the "id" report.
     *
     * @param id the report to read.
     * @return the file, with the content type it was recognised as when it was stored — never one the uploader chose.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadReportFile(@PathVariable("id") String id) {
        log.debug("REST request to download the file of Report : {}", id);
        patientScope.requireRead(ClinicalDomain.DIAGNOSIS);
        return reportRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .flatMap(reportFiles::load)
            .map(ReportResource::asDownload)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code DELETE  /reports/:id/file} : remove the file attached to the "id" report, keeping the report itself.
     *
     * @param id the report to clear.
     * @return {@code 204 (NO_CONTENT)}, or {@code 404} when there is no such report in the caller's scope.
     */
    @DeleteMapping("/{id}/file")
    public ResponseEntity<Void> deleteReportFile(@PathVariable("id") String id) {
        log.debug("REST request to remove the file of Report : {}", id);
        Report report = reportRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).orElse(null);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        reportFiles.deleteFor(report);
        report.setUrl(null);
        reportRepository.save(report);
        return ResponseEntity.noContent().build();
    }

    /**
     * Wraps a stored file as a download.
     *
     * <p>{@code Content-Disposition: inline} so a PDF or an image opens in the browser rather than landing in the
     * downloads folder — the patient is looking at their own report, not collecting a file. The filename is quoted
     * and was stripped of separators and quotes before it was ever stored.</p>
     */
    private static ResponseEntity<Resource> asDownload(GridFsResource stored) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(stored.getContentType());
        } catch (IllegalArgumentException | IllegalStateException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity
            .ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + stored.getFilename() + "\"")
            .body(stored);
    }
}
