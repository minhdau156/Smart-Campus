package edu.campus.starter.events;

import edu.campus.kernel.event.DomainEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.modulith.events.RoutingTarget;

/**
 * The routing table of one app: which event type goes to which topic, and with which key. Built
 * from all {@link EventRoute} beans.
 *
 * <p>The key is computed here as a plain String and handed to Modulith in a {@link RoutingTarget}
 * (the approach recorded in T7.3). A missing or blank key throws, so the event is never sent
 * without the key its topic needs (spec D6).
 */
public final class EventRouting {

    private final Map<String, EventRoute> byType = new HashMap<>();

    public EventRouting(Collection<EventRoute> routes) {
        for (EventRoute route : routes) {
            EventRoute existing = byType.putIfAbsent(route.eventType(), route);
            if (existing != null) {
                throw new IllegalStateException("Event type '%s' has two routes: to %s and to %s"
                        .formatted(route.eventType(), existing.topicName(), route.topicName()));
            }
        }
    }

    /** True if this object is an envelope whose type has a route: it goes to Kafka. */
    public boolean isRouted(Object event) {
        return event instanceof DomainEvent<?> envelope && byType.containsKey(envelope.type());
    }

    /**
     * The topic and key for this event.
     *
     * @throws IllegalArgumentException if the type has no route, or the key is missing or blank
     */
    public RoutingTarget target(DomainEvent<?> event) {
        EventRoute route = byType.get(event.type());
        if (route == null) {
            throw new IllegalArgumentException("No topic for event type '%s'".formatted(event.type()));
        }
        String key = route.key().apply(event);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Event '%s' to topic %s has no message key (spec D6)".formatted(event.type(), route.topicName()));
        }
        return RoutingTarget.forTarget(route.topicName()).andKey(key);
    }
}
