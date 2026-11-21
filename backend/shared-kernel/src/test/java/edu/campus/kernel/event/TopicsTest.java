package edu.campus.kernel.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import edu.campus.kernel.id.TypedId;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TopicsTest {

    private static final Instant AT = Instant.parse("2026-09-07T08:00:00Z");

    /** The Arch §6 table (spec D6). If this test fails, the spec must change first. */
    @ParameterizedTest
    @CsvSource({
        "REGISTRATION_REQUESTS, registration.requests, 12, sectionId,   false",
        "REGISTRATION_EVENTS,   registration.events,    6, studentId,   true",
        "COURSE_EVENTS,         course.events,          3, sectionId,   true",
        "GRADE_EVENTS,          grade.events,           3, studentId,   true",
        "CAMPUS_EVENTS,         campus.events,          3, aggregateId, true",
    })
    void topicMatchesTheArchitectureTable(
            Topics topic, String name, int partitions, String keyField, boolean replayable) {
        assertThat(topic.topicName()).isEqualTo(name);
        assertThat(topic.partitions()).isEqualTo(partitions);
        assertThat(topic.keyField()).isEqualTo(keyField);
        assertThat(topic.replayable()).isEqualTo(replayable);
    }

    @Test
    void thereAreExactlyFiveTopics() {
        assertThat(Arrays.stream(Topics.values()).map(Topics::topicName))
                .containsExactlyInAnyOrder(
                        "registration.requests",
                        "registration.events",
                        "course.events",
                        "grade.events",
                        "campus.events");
    }

    @Test
    void byNameFindsATopic() {
        assertThat(Topics.byName("grade.events")).isEqualTo(Topics.GRADE_EVENTS);
    }

    @Test
    void byNameFailsForAnUnknownTopic() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Topics.byName("grades"))
                .withMessageContaining("grades");
    }

    // --- Key extraction (spec D6: the key is NOT always the aggregateId) ---

    record SectionId(UUID value) implements TypedId {}

    record EnrollmentConfirmed(String studentId, SectionId sectionId) {}

    record NoKeyFields(String something) {}

    private static final SectionId SECTION = new SectionId(UUID.fromString("7c1e2a40-0000-4000-8000-000000000001"));

    private static <P> DomainEvent<P> event(String aggregateId, P payload) {
        return new DomainEvent<>(UUID.randomUUID(), "SomethingHappened", 1, AT, aggregateId, payload);
    }

    @Test
    void registrationEventsAreKeyedByStudentNotByAggregate() {
        var e = event("section-1234", new EnrollmentConfirmed("student-42", SECTION));

        assertThat(Topics.REGISTRATION_EVENTS.keyOf(e)).isEqualTo("student-42");
    }

    @Test
    void aTypedIdKeyUsesItsUuidValue() {
        var e = event("section-1234", new EnrollmentConfirmed("student-42", SECTION));

        assertThat(Topics.COURSE_EVENTS.keyOf(e)).isEqualTo("7c1e2a40-0000-4000-8000-000000000001");
    }

    @Test
    void campusEventsAreKeyedByAggregateId() {
        var e = event("building-7", new NoKeyFields("x"));

        assertThat(Topics.CAMPUS_EVENTS.keyOf(e)).isEqualTo("building-7");
    }

    @Test
    void aPayloadWithoutTheKeyFieldFails() {
        var e = event("section-1234", new NoKeyFields("x"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Topics.GRADE_EVENTS.keyOf(e))
                .withMessageContaining("grade.events")
                .withMessageContaining("studentId");
    }

    @Test
    void aNullKeyValueFails() {
        var e = event("section-1234", new EnrollmentConfirmed(null, SECTION));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Topics.REGISTRATION_EVENTS.keyOf(e))
                .withMessageContaining("studentId");
    }

    @Test
    void aPayloadThatIsNotARecordFails() {
        var e = event("section-1234", "just a string");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Topics.REGISTRATION_REQUESTS.keyOf(e))
                .withMessageContaining("record");
    }
}
