package edu.campus.starter.events;

import edu.campus.kernel.event.DomainEvent;
import edu.campus.kernel.event.Topics;
import java.util.Objects;
import java.util.function.Function;

/**
 * One line of the routing table: events of this {@code type} go to this Kafka topic, with this
 * key. An app declares one bean per event type it sends to Kafka:
 *
 * <pre>{@code
 * @Bean
 * EventRoute enrollmentConfirmed() {
 *     return EventRoute.to(Topics.REGISTRATION_EVENTS, "EnrollmentConfirmed");
 * }
 * }</pre>
 *
 * The starter collects all routes into {@link EventRouting} and tells Modulith to externalize
 * exactly these events (spec D1, D6).
 *
 * @param eventType the envelope's {@code type}, e.g. {@code "EnrollmentConfirmed"}
 * @param topicName the Kafka topic
 * @param key computes the record key from the event; may return null, which then fails the
 *     publication
 */
public record EventRoute(String eventType, String topicName, Function<DomainEvent<?>, String> key) {

    public EventRoute {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(topicName, "topicName");
        Objects.requireNonNull(key, "key");
    }

    /** The normal case: a topic from the shared {@link Topics} table, keyed as that table says. */
    public static EventRoute to(Topics topic, String eventType) {
        return new EventRoute(eventType, topic.topicName(), topic::keyOf);
    }

    /** A topic outside the {@link Topics} table (dev-only, e.g. the outbox probe). */
    public static EventRoute custom(String eventType, String topicName, Function<DomainEvent<?>, String> key) {
        return new EventRoute(eventType, topicName, key);
    }
}
