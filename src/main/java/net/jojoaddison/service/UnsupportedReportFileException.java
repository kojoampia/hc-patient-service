package net.jojoaddison.service;

/**
 * A report file that will not be stored: empty, too large, or not a format this accepts.
 *
 * <p>Carries a message meant for the person who chose the file, not for a log — the web layer puts it straight into
 * the response. "That file is not one of: application/pdf, image/jpeg, …" tells them what to do next; "invalid file"
 * does not.</p>
 */
public class UnsupportedReportFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedReportFileException(String message) {
        super(message);
    }
}
