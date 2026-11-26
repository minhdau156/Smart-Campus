package edu.campus.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.kernel.event.DomainEvent;
import edu.campus.starter.events.EventRouting;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.RoutingTarget;

class ProbeTopicTest {

    private static DomainEvent<Object> event(String type, Object payload) {
        return new DomainEvent<>(UUID.randomUUID(), type, 1, Instant.EPOCH, "probe-7", payload);
    }

    private final EventRouting routing = new EventRouting(List.of(new OutboxProbeConfiguration().probeRoute()));

    @Test
    void routesProbeEventsToTheProbeTopicKeyedByAggregateId() {
        RoutingTarget target = routing.target(event(OutboxProbeService.EVENT_TYPE, Map.of()));

        assertThat(target.getTarget()).isEqualTo("platform.probe");
        assertThat(target.getKey()).isEqualTo("probe-7");
    }

    @Test
    void keyDoesNotDependOnThePayloadJavaType() {
        // After a restart the registry reads the event back from JSON: the payload may then be a Map,
        // not an OutboxProbe. The key must be the same either way.
        OutboxProbe typed = new OutboxProbe(UUID.randomUUID(), "x", Instant.EPOCH);

        assertThat(ProbeTopic.keyOf(event(OutboxProbeService.EVENT_TYPE, typed)))
                .isEqualTo(ProbeTopic.keyOf(event(OutboxProbeService.EVENT_TYPE, Map.of("note", "x"))))
                .isEqualTo("probe-7");
    }

    @Test
    void selectsOnlyProbeEvents() {
        assertThat(routing.isRouted(event(OutboxProbeService.EVENT_TYPE, Map.of())))
                .isTrue();
        assertThat(routing.isRouted(event("SomethingElse", Map.of()))).isFalse();
    }
}
