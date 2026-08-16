package net.jojoaddison.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.Report;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The file behind a {@link Report} — stored in GridFS, in the database this service already uses.
 *
 * <p>Chosen over a host volume or an object store on 2026-08-16 because it adds nothing to run: no second service, no
 * port, no credentials, and one backup covers the documents and their files together. The quality machine and the
 * production machine are maintained by the architect rather than from this workspace, so a design that needs a
 * directory provisioned on each of them costs a hand-over every time it changes; this one costs nothing.</p>
 *
 * <h2>The type is decided from the bytes</h2>
 *
 * <p>A filename is user input. {@code results.pdf} says nothing about what the file is, and on a clinical record the
 * consequence of believing it is that whatever was uploaded is later served back to a browser with a content type the
 * uploader chose. Every accepted format has a stable signature in its first bytes, so that is what is read; the
 * submitted name is kept for display and never used as a path or as evidence.</p>
 */
@Service
public class ReportFileService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportFileService.class);

    /** What a patient may file, decided 2026-08-16: a clinic PDF and a photographed lab slip. */
    private static final List<Signature> ACCEPTED = List.of(
        new Signature("application/pdf", new byte[] { 0x25, 0x50, 0x44, 0x46 }, 0), // %PDF
        new Signature("image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, 0),
        new Signature("image/png", new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, 0),
        // HEIC carries its brand at offset 4, after the box length: "ftypheic" and friends.
        new Signature("image/heic", "ftyp".getBytes(), 4)
    );

    /** Ten megabytes. A phone photograph of a lab slip is comfortably under it; a scanned booklet is not. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Enough of the file to recognise it — the longest signature we look for ends well inside this. */
    private static final int HEAD_BYTES = 16;

    private final GridFsOperations gridFs;

    public ReportFileService(GridFsOperations gridFs) {
        this.gridFs = gridFs;
    }

    /** The content types this accepts, for the message shown when one is refused. */
    public static List<String> acceptedTypes() {
        return ACCEPTED.stream().map(Signature::contentType).toList();
    }

    /**
     * Reads the first bytes of a file and says what it actually is.
     *
     * @param head the first {@value #HEAD_BYTES} bytes, or fewer for a very small file.
     * @return the content type, or empty when the bytes match nothing this accepts.
     */
    public static Optional<String> detectType(byte[] head) {
        return ACCEPTED.stream().filter(signature -> signature.matches(head)).map(Signature::contentType).findFirst();
    }

    /**
     * Stores a file against a report, replacing any file it already had.
     *
     * @param report the report the file belongs to; its id and patient are recorded on the stored file so a stray
     *     object can always be traced back to the record that owns it.
     * @param file the uploaded file.
     * @return the id of the stored object, for {@link Report#setUrl}.
     * @throws UnsupportedReportFileException when the bytes are not one of {@link #acceptedTypes()} or the file is
     *     empty or over {@link #MAX_BYTES}.
     * @throws IOException if the upload cannot be read.
     */
    public String store(Report report, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedReportFileException("The file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new UnsupportedReportFileException("The file is larger than " + (MAX_BYTES / 1024 / 1024) + " MB.");
        }

        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(HEAD_BYTES);
        }
        String contentType = detectType(head)
            .orElseThrow(() -> new UnsupportedReportFileException("That file is not one of: " + String.join(", ", acceptedTypes()) + "."));

        // Replacing rather than accumulating: a report has one file, and re-uploading is how a patient corrects a
        // photograph they took badly. Without this the old object stays in the bucket, unreferenced, forever.
        deleteFor(report);

        Document metadata = new Document(Map.of("reportId", report.getId(), "patientId", String.valueOf(report.getPatientId())));
        try (InputStream in = file.getInputStream()) {
            String storedId = gridFs.store(in, safeName(file.getOriginalFilename()), contentType, metadata).toString();
            LOG.debug("Stored {} ({} bytes, {}) for report {}", storedId, file.getSize(), contentType, report.getId());
            return storedId;
        }
    }

    /** The stored file for a report, or empty when it has none. */
    public Optional<GridFsResource> load(Report report) {
        GridFSFile stored = gridFs.findOne(Query.query(Criteria.where("metadata.reportId").is(report.getId())));
        return Optional.ofNullable(stored).map(gridFs::getResource);
    }

    /** Removes whatever file a report has, if any. Safe to call when it has none. */
    public void deleteFor(Report report) {
        gridFs.delete(Query.query(Criteria.where("metadata.reportId").is(report.getId())));
    }

    /**
     * The submitted filename, reduced to something safe to store and to echo back.
     *
     * <p>Path separators and leading dots go: the name is only ever shown to a person, but it travels through a
     * {@code Content-Disposition} header on the way there, and a name is the wrong thing to trust in one.</p>
     */
    private static String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "report";
        }
        String cleaned = original.replaceAll("[\\\\/\\r\\n\"]", "_").replaceAll("^\\.+", "").trim();
        return cleaned.isEmpty() ? "report" : cleaned.substring(0, Math.min(cleaned.length(), 120));
    }

    /** A magic-byte signature: the bytes a format starts with, and where in the file they sit. */
    private record Signature(String contentType, byte[] magic, int offset) {
        boolean matches(byte[] head) {
            if (head.length < offset + magic.length) {
                return false;
            }
            return Arrays.equals(head, offset, offset + magic.length, magic, 0, magic.length);
        }
    }
}
