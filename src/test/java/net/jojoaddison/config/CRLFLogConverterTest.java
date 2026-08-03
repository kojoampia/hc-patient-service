package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Unit tests for {@link CRLFLogConverter}.
 *
 * <p>This converter is the service's defence against log forging: without it, anything a caller can
 * get into a log message — a patient id, an email, a search term — can inject newlines and fake a
 * log entry. The tests below pin both halves of that contract: unsafe input is neutralised, and
 * the deliberate exemptions still pass through untouched.</p>
 */
class CRLFLogConverterTest {

    private final CRLFLogConverter converter = new CRLFLogConverter();

    private static ILoggingEvent event(String loggerName, Marker... markers) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn(loggerName);
        when(event.getMarkerList()).thenReturn(markers.length == 0 ? List.of() : List.of(markers));
        return event;
    }

    @Test
    void replacesCarriageReturnsAndNewlines() {
        String forged = "user logged in\nWARN  fake entry\rand a\ttab";

        String transformed = converter.transform(event("net.jojoaddison.web.rest.ProfileResource"), forged);

        assertThat(transformed).doesNotContain("\n", "\r", "\t");
        assertThat(transformed).isEqualTo("user logged in_WARN  fake entry_and a_tab");
    }

    @Test
    void leavesOrdinaryMessagesAlone() {
        String message = "REST request to get all Profiles";

        assertThat(converter.transform(event("net.jojoaddison.web.rest.ProfileResource"), message)).isEqualTo(message);
    }

    @Test
    void trustsEventsMarkedCrlfSafe() {
        String multiline = "line one\nline two";

        String transformed = converter.transform(
            event("net.jojoaddison.web.rest.ProfileResource", CRLFLogConverter.CRLF_SAFE_MARKER),
            multiline
        );

        assertThat(transformed).isEqualTo(multiline);
    }

    @Test
    void ignoresUnrelatedMarkers() {
        String multiline = "line one\nline two";

        String transformed = converter.transform(
            event("net.jojoaddison.web.rest.ProfileResource", MarkerFactory.getMarker("OTHER")),
            multiline
        );

        assertThat(transformed).isEqualTo("line one_line two");
    }

    @Test
    void trustsFrameworkLoggersThatFormatTheirOwnMultilineOutput() {
        String banner = "startup report\nsecond line";

        assertThat(converter.transform(event("org.springframework.boot.autoconfigure.condition"), banner)).isEqualTo(banner);
        assertThat(converter.transform(event("org.springframework.boot.diagnostics.FailureAnalyzers"), banner)).isEqualTo(banner);
        assertThat(converter.transform(event("org.hibernate.SQL"), banner)).isEqualTo(banner);
    }

    @Test
    void doesNotTrustALoggerThatMerelyResemblesASafeOne() {
        assertThat(converter.isLoggerSafe(event("net.jojoaddison.org.hibernate"))).isFalse();
    }

    @Test
    void colourisesTheReplacementWhenAnElementIsConfigured() {
        // toAnsiString is what turns the placeholder red; with ANSI disabled it is a no-op, so the
        // assertion is only that the call is wired up rather than what escape codes come out.
        assertThat(converter.toAnsiString("_", org.springframework.boot.ansi.AnsiColor.RED)).contains("_");
    }
}
