package edu.campus.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

import edu.campus.KafkaTestConsumer;
import edu.campus.TestcontainersConfiguration;
import edu.campus.kernel.event.DomainEvent;
import edu.campus.kernel.event.Topics;
import edu.campus.kernel.id.TypedId;
import edu.campus.starter.events.EventRoute;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Spec D6 / O5 for the topics core-app produces: each event reaches its topic with the key the
 * {@link Topics} table defines, and an event without that key is never published.
 *
 * <p>No feature module exists yet, so the test declares its own event types and routes, the same
 * way a module will ({@link EventRoute} beans).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, EventKeysIT.TestRoutes.class})
class EventKeysIT {

    record SectionId(UUID value) implements TypedId {}

    record SectionChanged(SectionId sectionId, String title) {}

    record GradePosted(String studentId, String courseId) {}

    record BuildingClosed(String reason) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRoutes {
        @Bean
        EventRoute sectionChanged() {
            return EventRoute.to(Topics.COURSE_EVENTS, "TestSectionChanged");
        }

        @Bean
        EventRoute gradePosted() {
            return EventRoute.to(Topics.GRADE_EVENTS, "TestGradePosted");
        }

        @Bean
        EventRoute buildingClosed() {
            return EventRoute.to(Topics.CAMPUS_EVENTS, "TestBuildingClosed");
        }
    }

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void courseEventsAreKeyedBySectionId() {
        UUID section = UUID.randomUUID();

        publish("TestSectionChanged", "course-1", new SectionChanged(new SectionId(section), "Algebra"));

        assertOneRecord(Topics.COURSE_EVENTS, section.toString());
    }

    @Test
    void gradeEventsAreKeyedByStudentIdNotByAggregateId() {
        String student = "student-" + UUID.randomUUID();

        publish("TestGradePosted", "course-1", new GradePosted(student, "course-1"));

        assertOneRecord(Topics.GRADE_EVENTS, student);
    }

    @Test
    void campusEventsAreKeyedByAggregateId() {
        String building = "building-" + UUID.randomUUID();

        publish("TestBuildingClosed", building, new BuildingClosed("storm"));

        assertOneRecord(Topics.CAMPUS_EVENTS, building);
    }

    @Test
    void anEventWithoutItsKeyFailsAndRollsBackTheChange() {
        String course = "course-" + UUID.randomUUID();
        var noStudent = new GradePosted(null, course);
        Integer before = publicationCount();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> publish("TestGradePosted", course, noStudent))
                .withMessageContaining("studentId");

        // Nothing was saved for the outbox, so nothing can ever be sent.
        assertThat(publicationCount()).isEqualTo(before);
    }

    private void publish(String type, String aggregateId, Object payload) {
        transaction.executeWithoutResult(status -> events.publishEvent(
                new DomainEvent<>(UUID.randomUUID(), type, 1, Instant.now(), aggregateId, payload)));
    }

    private Integer publicationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM platform.event_publication", Integer.class);
    }

    private void assertOneRecord(Topics topic, String key) {
        var reader = new KafkaTestConsumer(kafka.getBootstrapServers(), topic.topicName());
        List<ConsumerRecord<String, String>> records = await().atMost(Duration.ofSeconds(20))
                .until(() -> reader.recordsWithKey(key), found -> !found.isEmpty());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().key()).isEqualTo(key);
    }
}
