package edu.campus.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

import edu.campus.kernel.event.DomainEvent;
import edu.campus.kernel.event.Topics;
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
 * Spec D6 / O5 for the topics registration-service produces: each event reaches its topic with the
 * key the {@link Topics} table defines (Arch §6), and an event without that key is never published.
 *
 * <p>The registration features come later, so the test declares its own event types and routes,
 * the same way the real code will ({@link EventRoute} beans).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, EventKeysIT.TestRoutes.class})
class EventKeysIT {

    record SeatRequested(String sectionId, String studentId) {}

    record EnrollmentConfirmed(String studentId, String sectionId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRoutes {
        @Bean
        EventRoute seatRequested() {
            return EventRoute.to(Topics.REGISTRATION_REQUESTS, "TestSeatRequested");
        }

        @Bean
        EventRoute enrollmentConfirmed() {
            return EventRoute.to(Topics.REGISTRATION_EVENTS, "TestEnrollmentConfirmed");
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
    void registrationRequestsAreKeyedBySectionId() {
        // Per-section order is what keeps the waitlist fair (Arch §6).
        String section = "section-" + UUID.randomUUID();

        publish("TestSeatRequested", "request-1", new SeatRequested(section, "student-1"));

        assertOneRecord(Topics.REGISTRATION_REQUESTS, section);
    }

    @Test
    void registrationEventsAreKeyedByStudentIdNotBySection() {
        String student = "student-" + UUID.randomUUID();
        String section = "section-" + UUID.randomUUID();

        publish("TestEnrollmentConfirmed", section, new EnrollmentConfirmed(student, section));

        assertOneRecord(Topics.REGISTRATION_EVENTS, student);
    }

    @Test
    void anEventWithoutItsKeyFailsAndRollsBackTheChange() {
        var noSection = new SeatRequested(null, "student-1");
        Integer before = publicationCount();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> publish("TestSeatRequested", "request-2", noSection))
                .withMessageContaining("sectionId");

        assertThat(publicationCount()).isEqualTo(before);
    }

    private void publish(String type, String aggregateId, Object payload) {
        transaction.executeWithoutResult(status -> events.publishEvent(
                new DomainEvent<>(UUID.randomUUID(), type, 1, Instant.now(), aggregateId, payload)));
    }

    private Integer publicationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM registration.event_publication", Integer.class);
    }

    private void assertOneRecord(Topics topic, String key) {
        var reader = new KafkaTestConsumer(kafka.getBootstrapServers(), topic.topicName());
        List<ConsumerRecord<String, String>> records = await().atMost(Duration.ofSeconds(20))
                .until(() -> reader.recordsWithKey(key), found -> !found.isEmpty());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().key()).isEqualTo(key);
    }
}
