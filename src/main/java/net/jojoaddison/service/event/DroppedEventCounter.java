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
 *
 * <h2>⭐ A second dimension since item 46: the topic stopped being enough</h2>
 *
 * <p>{@code patient.event} now carries two frame families behind two queues, and until this tag existed a drop on
 * either incremented one counter under one {@code topic} tag — so the one question the two queues exist to answer,
 * <em>did a burst of ordinary saves cost somebody a letter</em>, was answerable only by a substring of a WARN.
 * {@link #FAMILY_DIMENSION} splits it.</p>
 *
 * <p>⚠ <strong>The tag is required of every registration rather than optional, and that is a constraint rather than
 * tidiness.</strong> Prometheus and OTLP both require every meter of one name to carry the same tag <em>keys</em>, so a
 * family tag on one publisher and not the others would be a registry that refuses to scrape — the failure would land
 * on the whole meter, not on the stream that was careless. Making it a parameter means a fourth publisher cannot
 * forget it.</p>
 *
 * <p>Existing dashboards are unaffected: adding a label never stops a selector that does not mention it from matching,
 * and {@code sum by (topic)} still answers what it answered before.</p>
 */
final class DroppedEventCounter {

    public static final String METER_NAME = "events.publishing.dropped";
    public static final String METER_DESCRIPTION =
        "Frames dropped because a publisher's queue was full. The write always succeeded; the event was lost.";
    public static final String METER_BASE_UNIT = "events";
    /** The destination topic of the frame that was lost — the operator-facing vocabulary, not a class name. */
    public static final String TOPIC_DIMENSION = "topic";

    /**
     * Which kind of frame was lost, for the two streams where the topic does not say.
     *
     * <p>The values are {@link #LIFECYCLE}, {@link #NOTIFICATION}, {@link #COMMAND} and {@link #FAN_OUT} — what an
     * operator has lost, in their vocabulary: a command is a letter that will not be sent, a notification is a hole in
     * hc-admin's audit trail, a fan-out is a browser that refreshes a moment later.</p>
     */
    public static final String FAMILY_DIMENSION = "family";

    /** {@code patient-events} — the seven curated moments hc-admin's watermark is built from. */
    public static final String LIFECYCLE = "lifecycle";

    /** {@code patient.event}, {@code type=EntityChanged} — one audit row hc-admin will not be able to write. */
    public static final String NOTIFICATION = "notification";

    /** {@code patient.event}, a command type — a letter to a patient or their care angel that will not be sent. */
    public static final String COMMAND = "command";

    /** {@code patient-membership-events} — a push a browser will pick up on its next reload instead. */
    public static final String FAN_OUT = "fanout";

    private DroppedEventCounter() {}

    /**
     * @param topic the destination that lost the frame.
     * @param family one of the four constants above. Required — see the class javadoc on why it is not optional.
     */
    static Counter register(MeterRegistry registry, String topic, String family) {
        return Counter
            .builder(METER_NAME)
            .baseUnit(METER_BASE_UNIT)
            .description(METER_DESCRIPTION)
            .tag(TOPIC_DIMENSION, topic)
            .tag(FAMILY_DIMENSION, family)
            .register(registry);
    }
}
