package edu.campus.platform.internal;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Dev/test only (T7 outbox spike). The topic for probe events, written like an entry of the
 * shared-kernel {@code Topics} table: a name, a partition count and the rule for the message key.
 *
 * <p><b>How the key is set</b> (T12 builds on this): Modulith asks our
 * {@code EventExternalizationConfiguration} where an event goes. {@link #route} answers with a
 * {@link RoutingTarget}: the topic name plus the key, already computed as a plain string. Modulith
 * puts that string in the {@code kafka_messageKey} header and {@code KafkaTemplate} sends it as the
 * record key. No SpEL expression and no annotation on the event are needed.
 *
 * <p>The key is the envelope's {@code aggregateId}, not a payload field. Reason: when an event is
 * sent again after a failure or a restart, Modulith reads it back from JSON in the registry, and the
 * payload is then a {@code Map}, not an {@code OutboxProbe}. The envelope fields are always typed.
 */
final class ProbeTopic {

    static final String NAME = "platform.probe";
    static final int PARTITIONS = 3;

    private ProbeTopic() {}

    /** True for probe envelopes: these, and only these, go to {@link #NAME}. */
    static boolean isProbeEvent(Object event) {
        return event instanceof DomainEvent<?> envelope && OutboxProbeService.EVENT_TYPE.equals(envelope.type());
    }

    /** The key of a probe event: its aggregateId (the probe id). */
    static String keyOf(DomainEvent<?> event) {
        return event.aggregateId();
    }

    /** Topic + key for one probe event. */
    static RoutingTarget route(DomainEvent<?> event) {
        if (!isProbeEvent(event)) {
            throw new IllegalArgumentException("Not a probe event: '%s'".formatted(event.type()));
        }
        return RoutingTarget.forTarget(NAME).andKey(keyOf(event));
    }
}
