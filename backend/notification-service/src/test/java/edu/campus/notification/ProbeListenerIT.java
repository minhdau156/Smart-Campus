package edu.campus.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import edu.campus.kernel.event.DomainEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec O4 end to end over a real Kafka: the probe listener gets the SAME event twice (same
 * {@code eventId}), as happens after an outbox resend (spec O3), and writes one row.
 *
 * <p>How we know the second copy was handled (and skipped), not just "not yet arrived": after the
 * two copies we send a third, different event with the same key. Kafka keeps order per key, so when
 * the third event's row is there, both copies before it were processed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProbeListenerIT {

    private static final String TOPIC = "platform.probe";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void openProducer() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterEach
    void closeProducer() {
        producer.close();
    }

    @Test
    void theSameEventDeliveredTwiceGivesOneRow() throws Exception {
        UUID probeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String copy = envelope(eventId, probeId, "sent twice");

        send(probeId, copy);
        send(probeId, copy);
        UUID markerId = UUID.randomUUID();
        send(probeId, envelope(markerId, probeId, "marker"));

        await().atMost(Duration.ofSeconds(30)).until(() -> rows(markerId) == 1);
        assertThat(rows(eventId)).as("the second copy was skipped").isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification.processed_events WHERE consumer = ? AND event_id = ?",
                        Integer.class,
                        ProbeListener.CONSUMER,
                        eventId))
                .isOne();
    }

    @Test
    void aNewEventIsSavedWithItsData() throws Exception {
        UUID probeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(probeId, envelope(eventId, probeId, "hello"));

        await().atMost(Duration.ofSeconds(30)).until(() -> rows(eventId) == 1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT probe_id, note FROM notification.probe_read_model WHERE event_id = ?", eventId);
        assertThat(row).containsEntry("probe_id", probeId).containsEntry("note", "hello");
    }

    /** The envelope as core-app's outbox writes it: DomainEvent with the probe as payload. */
    private String envelope(UUID eventId, UUID probeId, String note) {
        Instant now = Instant.parse("2026-11-24T08:00:00Z");
        return json.writeValueAsString(new DomainEvent<>(
                eventId,
                "OutboxProbeCreated",
                1,
                now,
                probeId.toString(),
                Map.of("id", probeId.toString(), "note", note, "createdAt", now.toString())));
    }

    private void send(UUID key, String value) throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, key.toString(), value)).get();
    }

    private int rows(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification.probe_read_model WHERE event_id = ?", Integer.class, eventId);
    }
}
