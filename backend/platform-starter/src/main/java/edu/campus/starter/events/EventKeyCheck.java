package edu.campus.starter.events;

import edu.campus.kernel.event.DomainEvent;
import org.springframework.context.event.EventListener;

/**
 * Fails {@code publishEvent(...)} at once when a routed event has no message key (spec D6).
 *
 * <p>Why a listener: Modulith computes the topic and key only <i>after</i> the commit, when it
 * sends the event. A missing key found then would leave a saved business change with an event
 * that can never be sent. This plain {@code @EventListener} runs inside {@code publishEvent},
 * in the caller's transaction, so the exception rolls back the whole change instead.
 *
 * <p>It is platform code, not a module, so the Kafka-only rule (spec D5) does not apply to it.
 */
public class EventKeyCheck {

    private final EventRouting routing;

    public EventKeyCheck(EventRouting routing) {
        this.routing = routing;
    }

    @EventListener
    public void check(DomainEvent<?> event) {
        if (routing.isRouted(event)) {
            routing.target(event); // throws if the key is missing
        }
    }
}
