package edu.campus.platform.internal;

import edu.campus.kernel.event.DomainEvent;

/**
 * Dev/test only (T7 outbox spike). The topic for probe events, written like an entry of the
 * shared-kernel {@code Topics} table: a name, a partition count and the rule for the message key.
 *
 * <p><b>How the key is set</b> (since T12 shared by all events): {@link OutboxProbeConfiguration}
 * declares an {@code EventRoute} with this name and key rule. platform-starter's
 * {@code EventRouting} turns it into a Modulith {@code RoutingTarget}: the topic name plus the key,
 * already computed as a plain string. Modulith puts that string in the {@code kafka_messageKey}
 * header and {@code KafkaTemplate} sends it as the record key.
 *
 * <p>The key is the envelope's {@code aggregateId}, not a payload field. Reason: when an event is
 * sent again after a failure or a restart, Modulith reads it back from JSON in the registry, and the
 * payload is then a {@code Map}, not an {@code OutboxProbe}. The envelope fields are always typed.
 */
final class ProbeTopic {

    static final String NAME = "platform.probe";
    static final int PARTITIONS = 3;

    private ProbeTopic() {}

    /** The key of a probe event: its aggregateId (the probe id). */
    static String keyOf(DomainEvent<?> event) {
        return event.aggregateId();
    }
}
