package net.jojoaddison.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The one definition of the dropped-frame counter, so three publishers cannot drift on its name, unit or tag.
 *
 * <p>Backlog item 73. Since item 71 every publisher here drops on a full queue, and each drop was a WARN — a grep,
 * not a graph, and nobody watches a log for an absence. The counter sits beside the WARN, never instead of it: the
 * log line still says <em>what</em> was lost, the counter makes <em>"did we drop anything this week"</em> answerable
 * without reading logs.</p>
 *
 * <p>One meter name with a {@code topic} tag, rather than a name per publisher — the house shape
 * ({@code SecurityMetersService}'s one name with a {@code cause} tag), and the shape a dashboard wants: sum for "any
 * loss at all", split by tag for <em>which stream</em>, which is the question that matters — a gap in
 * {@code patient-events} changes what hc-admin believes, where a gap in the membership stream costs a browser one
 * refresh.</p>
 */
final class DroppedEventCounter {

    public static final String METER_NAME = "events.publishing.dropped";
    public static final String METER_DESCRIPTION =
        "Frames dropped because a publisher's queue was full. The write always succeeded; the event was lost.";
    public static final String METER_BASE_UNIT = "events";
    /** The destination topic of the frame that was lost — the operator-facing vocabulary, not a class name. */
    public static final String TOPIC_DIMENSION = "topic";

    private DroppedEventCounter() {}

    static Counter register(MeterRegistry registry, String topic) {
        return Counter
            .builder(METER_NAME)
            .baseUnit(METER_BASE_UNIT)
            .description(METER_DESCRIPTION)
            .tag(TOPIC_DIMENSION, topic)
            .register(registry);
    }
}
