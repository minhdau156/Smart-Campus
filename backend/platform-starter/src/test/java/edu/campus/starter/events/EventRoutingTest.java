package edu.campus.starter.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import edu.campus.kernel.event.DomainEvent;
import edu.campus.kernel.event.Topics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.RoutingTarget;

class EventRoutingTest {

    record GradePosted(String studentId, String courseId) {}

    private static DomainEvent<GradePosted> gradePosted(String studentId) {
        return new DomainEvent<>(
                UUID.randomUUID(),
                "GradePosted",
                1,
                Instant.parse("2026-09-07T08:00:00Z"),
                "course-7",
                new GradePosted(studentId, "course-7"));
    }

    private final EventRouting routing = new EventRouting(List.of(EventRoute.to(Topics.GRADE_EVENTS, "GradePosted")));

    @Test
    void aRoutedEventGoesToItsTopicWithTheTopicsKey() {
        RoutingTarget target = routing.target(gradePosted("student-42"));

        assertThat(target.getTarget()).isEqualTo("grade.events");
        // grade.events is keyed by studentId (spec D6), not by the aggregateId "course-7".
        assertThat(target.getKey()).isEqualTo("student-42");
    }

    @Test
    void onlyEventsWithARouteAreExternalized() {
        var other =
                new DomainEvent<>(UUID.randomUUID(), "SomethingElse", 1, Instant.EPOCH, "x", new GradePosted("s", "c"));

        assertThat(routing.isRouted(gradePosted("student-42"))).isTrue();
        assertThat(routing.isRouted(other)).isFalse();
        assertThat(routing.isRouted("not an envelope")).isFalse();
    }

    @Test
    void aMissingKeyFailsThePublication() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> routing.target(gradePosted(null)))
                .withMessageContaining("studentId");
    }

    @Test
    void aBlankKeyFailsThePublication() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> routing.target(gradePosted(" ")))
                .withMessageContaining("grade.events");
    }

    @Test
    void anEventWithoutARouteCannotBeRouted() {
        var other = new DomainEvent<>(UUID.randomUUID(), "SomethingElse", 1, Instant.EPOCH, "x", "p");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> routing.target(other))
                .withMessageContaining("SomethingElse");
    }

    @Test
    void anEventTypeCanHaveOnlyOneRoute() {
        var twice = List.of(
                EventRoute.to(Topics.GRADE_EVENTS, "GradePosted"), EventRoute.to(Topics.CAMPUS_EVENTS, "GradePosted"));

        assertThatIllegalStateException()
                .isThrownBy(() -> new EventRouting(twice))
                .withMessageContaining("GradePosted");
    }

    @Test
    void aDevTopicCanUseItsOwnKeyRule() {
        var probe = EventRoute.custom("ProbeCreated", "platform.probe", DomainEvent::aggregateId);
        var event = new DomainEvent<>(UUID.randomUUID(), "ProbeCreated", 1, Instant.EPOCH, "probe-1", "p");

        RoutingTarget target = new EventRouting(List.of(probe)).target(event);

        assertThat(target.getTarget()).isEqualTo("platform.probe");
        assertThat(target.getKey()).isEqualTo("probe-1");
    }
}
