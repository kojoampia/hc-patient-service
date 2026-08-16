package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Report;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.ReportRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.ReportFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The file behind a report: uploading it, reading it back, and not reading somebody else's.
 *
 * <p>Built with the {@code jwt()} post-processor rather than {@code @WithMockUser} for the reason
 * {@code PatientScopeIT} gives: the identity that decides what a caller may see lives in the token's {@code email}
 * claim, and {@code @WithMockUser} produces no token at all. The scope tests here are the point of this class as much
 * as the upload mechanics are — a file endpoint that forgets to ask whose record it is hands a patient's lab results
 * to anyone who can guess an id.</p>
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReportFileResourceIT {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PATIENT_ID = "patient-alice";

    private static final String BOB_EMAIL = "bob@example.com";
    private static final String BOB_PATIENT_ID = "patient-bob";

    /** A minimal but genuine PDF: the header is what the service reads. */
    private static final byte[] PDF = "%PDF-1.7\nnot much of a document\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private GridFsOperations gridFs;

    private Report aliceReport;
    private Report bobReport;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        profileRepository.deleteAll();
        gridFs.delete(new org.springframework.data.mongodb.core.query.Query());

        profileRepository.save(new Profile().email(ALICE_EMAIL).patientId(ALICE_PATIENT_ID));
        profileRepository.save(new Profile().email(BOB_EMAIL).patientId(BOB_PATIENT_ID));

        aliceReport = reportRepository.save(new Report().patientId(ALICE_PATIENT_ID).name("Alice bloods"));
        bobReport = reportRepository.save(new Report().patientId(BOB_PATIENT_ID).name("Bob bloods"));
    }

    @Test
    void aPatientUploadsAFileAndReadsItBack() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(pdf("bloods.pdf")).with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("api/reports/" + aliceReport.getId() + "/file"));

        // The url is stored, so the portal's existing "Open file" button needs no special case.
        assertThat(reportRepository.findById(aliceReport.getId()).orElseThrow().getUrl())
            .isEqualTo("api/reports/" + aliceReport.getId() + "/file");

        restMockMvc
            .perform(get("/api/reports/{id}/file", aliceReport.getId()).with(alice()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"bloods.pdf\""))
            .andExpect(content().bytes(PDF));
    }

    @Test
    void theContentTypeComesFromTheBytesAndNotFromTheUpload() throws Exception {
        // The uploader says it is a PNG; the bytes say PDF. The bytes win, both on the way in and on the way out.
        MockMultipartFile lying = new MockMultipartFile("file", "x-ray.png", MediaType.IMAGE_PNG_VALUE, PDF);

        restMockMvc.perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(lying).with(alice())).andExpect(status().isOk());

        restMockMvc
            .perform(get("/api/reports/{id}/file", aliceReport.getId()).with(alice()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void anExecutableIsRefusedHoweverItIsNamed() throws Exception {
        byte[] elf = new byte[] { 0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00 };
        MockMultipartFile disguised = new MockMultipartFile("file", "results.pdf", MediaType.APPLICATION_PDF_VALUE, elf);

        restMockMvc
            .perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(disguised).with(alice()))
            .andExpect(status().isBadRequest());

        assertThat(reportRepository.findById(aliceReport.getId()).orElseThrow().getUrl()).isNull();
    }

    @Test
    void anEmptyFileIsRefused() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "nothing.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        restMockMvc
            .perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(empty).with(alice()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aPatientCannotAttachAFileToSomebodyElsesReport() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", bobReport.getId()).file(pdf("bloods.pdf")).with(alice()))
            .andExpect(status().isNotFound());

        assertThat(reportRepository.findById(bobReport.getId()).orElseThrow().getUrl()).isNull();
    }

    @Test
    void aPatientCannotReadSomebodyElsesFile() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", bobReport.getId()).file(pdf("bob.pdf")).with(bob()))
            .andExpect(status().isOk());

        // 404 rather than 403: whether Bob has a report at all is not Alice's business either.
        restMockMvc.perform(get("/api/reports/{id}/file", bobReport.getId()).with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void aReportWithNoFileIsNotFoundRatherThanEmpty() throws Exception {
        restMockMvc.perform(get("/api/reports/{id}/file", aliceReport.getId()).with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void uploadingAgainReplacesTheFileRatherThanAccumulating() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(pdf("first.pdf")).with(alice()))
            .andExpect(status().isOk());

        byte[] second = "%PDF-1.7\nthe corrected photograph\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        restMockMvc
            .perform(
                multipart("/api/reports/{id}/file", aliceReport.getId())
                    .file(new MockMultipartFile("file", "second.pdf", MediaType.APPLICATION_PDF_VALUE, second))
                    .with(alice())
            )
            .andExpect(status().isOk());

        restMockMvc
            .perform(get("/api/reports/{id}/file", aliceReport.getId()).with(alice()))
            .andExpect(content().bytes(second))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"second.pdf\""));

        // The point of replacing: the first object is gone, not orphaned in the bucket forever.
        assertThat(gridFs.find(new org.springframework.data.mongodb.core.query.Query()).into(new java.util.ArrayList<>())).hasSize(1);
    }

    @Test
    void removingTheFileKeepsTheReport() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", aliceReport.getId()).file(pdf("bloods.pdf")).with(alice()))
            .andExpect(status().isOk());

        restMockMvc.perform(delete("/api/reports/{id}/file", aliceReport.getId()).with(alice())).andExpect(status().isNoContent());

        assertThat(reportRepository.findById(aliceReport.getId())).isPresent();
        assertThat(reportRepository.findById(aliceReport.getId()).orElseThrow().getUrl()).isNull();
        restMockMvc.perform(get("/api/reports/{id}/file", aliceReport.getId()).with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void aPatientCannotRemoveSomebodyElsesFile() throws Exception {
        restMockMvc
            .perform(multipart("/api/reports/{id}/file", bobReport.getId()).file(pdf("bob.pdf")).with(bob()))
            .andExpect(status().isOk());

        restMockMvc.perform(delete("/api/reports/{id}/file", bobReport.getId()).with(alice())).andExpect(status().isNotFound());

        restMockMvc.perform(get("/api/reports/{id}/file", bobReport.getId()).with(bob())).andExpect(status().isOk());
    }

    @Test
    void theAcceptedTypesAreTheOnesTheServiceDeclares() {
        assertThat(ReportFileService.acceptedTypes()).contains("application/pdf", "image/jpeg", "image/png", "image/heic");
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, PDF);
    }

    private static RequestPostProcessor alice() {
        return patient(ALICE_EMAIL);
    }

    private static RequestPostProcessor bob() {
        return patient(BOB_EMAIL);
    }

    private static RequestPostProcessor patient(String email) {
        return jwt()
            .jwt(builder -> builder.claim(SecurityUtils.EMAIL_KEY, email))
            .authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER), new SimpleGrantedAuthority(AuthoritiesConstants.PATIENT));
    }
}
