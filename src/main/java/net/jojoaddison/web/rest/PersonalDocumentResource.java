package net.jojoaddison.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.PersonalDocument;
import net.jojoaddison.repository.PersonalDocumentRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.PatientScope;
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
 * REST controller for managing {@link net.jojoaddison.domain.PersonalDocument}.
 */
@RestController
@RequestMapping("/api/personal-documents")
public class PersonalDocumentResource {

    private final Logger log = LoggerFactory.getLogger(PersonalDocumentResource.class);

    private static final String ENTITY_NAME = "hcPatientServicePersonalDocument";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PersonalDocumentRepository personalDocumentRepository;

    private final PatientScope patientScope;

    public PersonalDocumentResource(PersonalDocumentRepository personalDocumentRepository, PatientScope patientScope) {
        this.personalDocumentRepository = personalDocumentRepository;
        this.patientScope = patientScope;
    }

    /**
     * {@code POST  /personal-documents} : Create a new personalDocument.
     *
     * @param personalDocument the personalDocument to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new personalDocument, or with status {@code 400 (Bad Request)} if the personalDocument has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PersonalDocument> createPersonalDocument(@RequestBody PersonalDocument personalDocument)
        throws URISyntaxException {
        log.debug("REST request to save PersonalDocument : {}", personalDocument);
        if (personalDocument.getId() != null) {
            throw new BadRequestAlertException("A new personalDocument cannot already have an ID", ENTITY_NAME, "idexists");
        }
        personalDocument.setPatientId(patientScope.requirePatientIdForWrite(personalDocument.getPatientId()));
        PersonalDocument result = personalDocumentRepository.save(personalDocument);
        return ResponseEntity
            .created(new URI("/api/personal-documents/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId()))
            .body(result);
    }

    /**
     * {@code PUT  /personal-documents/:id} : Updates an existing personalDocument.
     *
     * @param id the id of the personalDocument to save.
     * @param personalDocument the personalDocument to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated personalDocument,
     * or with status {@code 400 (Bad Request)} if the personalDocument is not valid,
     * or with status {@code 500 (Internal Server Error)} if the personalDocument couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PersonalDocument> updatePersonalDocument(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody PersonalDocument personalDocument
    ) throws URISyntaxException {
        log.debug("REST request to update PersonalDocument : {}, {}", id, personalDocument);
        if (personalDocument.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, personalDocument.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        PersonalDocument existing = personalDocumentRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        personalDocument.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), personalDocument.getPatientId()));

        PersonalDocument result = personalDocumentRepository.save(personalDocument);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, personalDocument.getId()))
            .body(result);
    }

    /**
     * {@code PATCH  /personal-documents/:id} : Partial updates given fields of an existing personalDocument, field will ignore if it is null
     *
     * @param id the id of the personalDocument to save.
     * @param personalDocument the personalDocument to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated personalDocument,
     * or with status {@code 400 (Bad Request)} if the personalDocument is not valid,
     * or with status {@code 404 (Not Found)} if the personalDocument is not found,
     * or with status {@code 500 (Internal Server Error)} if the personalDocument couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PersonalDocument> partialUpdatePersonalDocument(
        @PathVariable(value = "id", required = false) final String id,
        @RequestBody PersonalDocument personalDocument
    ) throws URISyntaxException {
        log.debug("REST request to partial update PersonalDocument partially : {}, {}", id, personalDocument);
        if (personalDocument.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, personalDocument.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        // Deliberately not existsById: the stored record has to be read to find out who owns it. "Not
        // yours" and "does not exist" raise the identical error, so this cannot be used to probe for
        // other patients' record ids.
        PersonalDocument existing = personalDocumentRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()))
            .orElseThrow(() -> new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound"));

        // A patient can never reassign a record by editing the payload — not their own, not anybody's.
        // An administrator or clinician still can, because refiling a misfiled record is legitimate work.
        personalDocument.setPatientId(patientScope.patientIdForUpdate(existing.getPatientId(), personalDocument.getPatientId()));

        Optional<PersonalDocument> result = personalDocumentRepository
            .findById(personalDocument.getId())
            .map(existingPersonalDocument -> {
                if (personalDocument.getName() != null) {
                    existingPersonalDocument.setName(personalDocument.getName());
                }
                if (personalDocument.getCategory() != null) {
                    existingPersonalDocument.setCategory(personalDocument.getCategory());
                }
                if (personalDocument.getUrl() != null) {
                    existingPersonalDocument.setUrl(personalDocument.getUrl());
                }
                if (personalDocument.getPatientId() != null) {
                    existingPersonalDocument.setPatientId(personalDocument.getPatientId());
                }
                if (personalDocument.getIssuedOn() != null) {
                    existingPersonalDocument.setIssuedOn(personalDocument.getIssuedOn());
                }
                if (personalDocument.getExpiresOn() != null) {
                    existingPersonalDocument.setExpiresOn(personalDocument.getExpiresOn());
                }

                return existingPersonalDocument;
            })
            .map(personalDocumentRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, personalDocument.getId())
        );
    }

    /**
     * {@code GET  /personal-documents} : get all the personalDocuments.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of personalDocuments in body.
     * @param patientId when present, restricts the result to that patient's records.
     */
    @GetMapping("")
    public List<PersonalDocument> getAllPersonalDocuments(@RequestParam(required = false) String patientId) {
        log.debug("REST request to get all PersonalDocuments for patient {}", patientId);
        return patientScope.findScoped(patientId, personalDocumentRepository::findAll, personalDocumentRepository::findByPatientId);
    }

    /**
     * {@code GET  /personal-documents/:id} : get the "id" personalDocument.
     *
     * @param id the id of the personalDocument to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the personalDocument, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PersonalDocument> getPersonalDocument(@PathVariable("id") String id) {
        log.debug("REST request to get PersonalDocument : {}", id);
        Optional<PersonalDocument> personalDocument = personalDocumentRepository
            .findById(id)
            .filter(current -> patientScope.isVisible(current.getPatientId()));
        return ResponseUtil.wrapOrNotFound(personalDocument);
    }

    /**
     * {@code DELETE  /personal-documents/:id} : delete the "id" personalDocument.
     *
     * <p><strong>{@code ROLE_ADMIN} only.</strong> Patient data is never deleted — see
     * {@link net.jojoaddison.security.PatientScope} for why a patient may not delete even their own
     * records, and what is meant to replace it.</p>
     *
     * @param id the id of the personalDocument to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePersonalDocument(@PathVariable("id") String id) {
        log.debug("REST request to delete PersonalDocument : {}", id);
        if (personalDocumentRepository.findById(id).filter(current -> patientScope.isVisible(current.getPatientId())).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        personalDocumentRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id)).build();
    }
}
