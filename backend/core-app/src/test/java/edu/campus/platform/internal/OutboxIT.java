package edu.campus.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import edu.campus.KafkaTestConsumer;
import edu.campus.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The outbox spike (spec D1, success criteria O1-O3): a real app, a real PostgreSQL and a real
 * Kafka broker. Each test uses its own probe id as the Kafka key, so tests do not see each other's
 * records.
 *
 * <p>Same annotations as {@code CoreApplicationIT}, so Spring reuses one started app for both.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxIT {

    @Autowired
    private OutboxProbeService probes;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper json;

    /** How often the retry job resubmits failed publications (the "relay interval"). */
    @Value("${campus.outbox.retry-interval}")
    private Duration retryInterval;

    /** The producer's delivery.timeout.ms from the test profile. */
    @Value("${spring.kafka.producer.properties.delivery.timeout.ms}")
    private long deliveryTimeoutMs;

    private KafkaTestConsumer topic;

    @BeforeEach
    void setUp() {
        topic = new KafkaTestConsumer(kafka.getBootstrapServers(), ProbeTopic.NAME);
    }

    // ----- O1: committed change -> exactly one Kafka record, full envelope, expected key -----

    @Test
    void o1_aCommittedProbeIsPublishedOnceWithTheFullEnvelopeAndItsKey() {
        UUID id = probes.create("hello outbox");

        List<ConsumerRecord<String, String>> records = awaitRecords(id, 1);
        ConsumerRecord<String, String> record = records.getFirst();

        // Key = the one defined by ProbeTopic (the probe id), so all events of one probe stay in order.
        assertThat(record.key()).isEqualTo(id.toString());

        // Value = the whole DomainEvent envelope (spec D4), not just the payload.
        JsonNode envelope = json.readTree(record.value());
        assertThat(UUID.fromString(envelope.get("eventId").asString())).isNotNull();
        assertThat(envelope.get("type").asString()).isEqualTo(OutboxProbeService.EVENT_TYPE);
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(Instant.parse(envelope.get("occurredAt").asString())).isNotNull();
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(id.toString());
        assertThat(envelope.at("/payload/id").asString()).isEqualTo(id.toString());
        assertThat(envelope.at("/payload/note").asString()).isEqualTo("hello outbox");

        // The registry marks it complete, and no second copy shows up later.
        await().atMost(Duration.ofSeconds(10)).until(() -> completionDate(id) != null);
        stillExactlyOneRecordAfter(twoRelayIntervals(), id);
    }

    // ----- O2: rolled-back change -> nothing is published -----

    @Test
    void o2_aRolledBackProbeIsNeverPublished() {
        UUID id = transaction.execute(status -> {
            UUID created = probes.create("will be rolled back");
            status.setRollbackOnly(); // the whole transaction, row and event, is undone
            return created;
        });

        // Wait at least 2x the relay interval (spec O2), checking the whole time that nothing arrives.
        await().during(twoRelayIntervals())
                .atMost(twoRelayIntervals().plusSeconds(10))
                .until(() -> topic.recordsWithKey(id.toString()).isEmpty());

        assertThat(count("SELECT count(*) FROM platform.outbox_probe WHERE id = ?", id))
                .isZero();
        assertThat(count("SELECT count(*) FROM platform.event_publication WHERE serialized_event LIKE ?", like(id)))
                .isZero();
    }

    // ----- O3: broker down after commit -> kept incomplete, delivered (at least once) when it is back -----
    // Spec O3 (revised at Checkpoint B): completion only after Kafka acknowledges; at least once;
    // every copy has the same eventId; a short outage gives exactly one copy.

    @Test
    void o3_shortOutage_eventWaitsAndIsDeliveredExactlyOnceAfterKafkaIsBack() {
        // Shorter than delivery.timeout.ms: the producer never gives up. It keeps the record and
        // resends the SAME batch; with idempotence on, the broker drops a repeat.
        Duration outage = deliveryTimeout().dividedBy(3);

        Outage result = createProbeWhileKafkaIsDown("short outage", outage);

        List<ConsumerRecord<String, String>> records = awaitRecords(result.id(), 1);
        assertFullEnvelope(records.getFirst(), result.id(), "short outage");
        assertCompletedOnlyAfter(result);
        stillExactlyOneRecordAfter(twoRelayIntervals(), result.id());
    }

    @Test
    void o3_longOutage_eventWaitsAndIsDeliveredAtLeastOnceWithOneEventId() {
        // Longer than delivery.timeout.ms: the producer gives up, the publication becomes FAILED, and
        // OutboxRetryJob sends it again when Kafka is back. The frozen broker may ALSO write the
        // request that was waiting in its network buffer, so 2 copies can arrive (seen in the spike).
        Duration outage = deliveryTimeout().plus(twoRelayIntervals());

        Outage result = createProbeWhileKafkaIsDown("long outage", outage);

        awaitRecords(result.id(), 1);
        assertCompletedOnlyAfter(result);
        // Let any second copy arrive, then check: 1 or 2 copies, all the very same event.
        await().pollDelay(twoRelayIntervals()).until(() -> true);
        List<ConsumerRecord<String, String>> records =
                topic.recordsWithKey(result.id().toString());
        assertThat(records).hasSizeBetween(1, 2);
        records.forEach(record -> assertFullEnvelope(record, result.id(), "long outage"));
        assertThat(records)
                .extracting(
                        record -> json.readTree(record.value()).get("eventId").asString())
                .as("every copy carries the same eventId, so consumers can drop repeats (D2)")
                .containsOnly(
                        json.readTree(records.getFirst().value()).get("eventId").asString());
    }

    /** What happened during one outage: the probe id and when Kafka came back. */
    private record Outage(UUID id, Instant kafkaBackAt) {}

    /**
     * Freezes Kafka, creates a probe, and checks during the whole outage that the commit succeeded
     * but the publication is NOT complete. Then unfreezes Kafka.
     */
    private Outage createProbeWhileKafkaIsDown(String note, Duration outage) {
        // Warm up: deliver one probe first, so the producer is already connected when Kafka stops
        // (the realistic case: the record is accepted into the producer's buffer).
        UUID warmUp = probes.create("warm-up");
        awaitRecords(warmUp, 1);

        UUID id;
        Instant kafkaBackAt;
        pause(kafka);
        try {
            id = probes.create(note);

            // The business commit succeeded, and the event is saved in the registry...
            assertThat(count("SELECT count(*) FROM platform.outbox_probe WHERE id = ?", id))
                    .isOne();
            assertThat(count("SELECT count(*) FROM platform.event_publication WHERE serialized_event LIKE ?", like(id)))
                    .isOne();
            // ...but it is NOT marked complete while the broker cannot acknowledge it.
            await().during(outage).atMost(outage.plusSeconds(10)).until(() -> completionDate(id) == null);
            assertThat(status(id)).isNotEqualTo("COMPLETED");
        } finally {
            // Taken just BEFORE unpausing: Kafka cannot acknowledge anything earlier than this. (Taken
            // after, it can be too late: the broker may answer within milliseconds of waking up.)
            kafkaBackAt = Instant.now();
            unpause(kafka);
        }
        return new Outage(id, kafkaBackAt);
    }

    /** O3's key promise: the publication is complete only after Kafka is back and acknowledged. */
    private void assertCompletedOnlyAfter(Outage outage) {
        await().atMost(Duration.ofSeconds(30)).until(() -> completionDate(outage.id()) != null);
        assertThat(completionDate(outage.id())).isAfter(outage.kafkaBackAt());
        assertThat(status(outage.id())).isEqualTo("COMPLETED");
    }

    /** Same key and full envelope, even when the event was read back from the registry and resent. */
    private void assertFullEnvelope(ConsumerRecord<String, String> record, UUID id, String note) {
        assertThat(record.key()).isEqualTo(id.toString());
        JsonNode envelope = json.readTree(record.value());
        assertThat(envelope.get("type").asString()).isEqualTo(OutboxProbeService.EVENT_TYPE);
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("aggregateId").asString()).isEqualTo(id.toString());
        assertThat(envelope.at("/payload/note").asString()).isEqualTo(note);
    }

    // ----- helpers -----

    private List<ConsumerRecord<String, String>> awaitRecords(UUID id, int expected) {
        return await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> topic.recordsWithKey(id.toString()), records -> records.size() >= expected);
    }

    private void stillExactlyOneRecordAfter(Duration wait, UUID id) {
        await().during(wait)
                .atMost(wait.plusSeconds(10))
                .untilAsserted(() -> assertThat(topic.recordsWithKey(id.toString()))
                        .extracting(ConsumerRecord::value)
                        .hasSize(1));
    }

    private Duration twoRelayIntervals() {
        return retryInterval.multipliedBy(2);
    }

    /** How long the producer keeps trying before it reports a send as failed (test profile). */
    private Duration deliveryTimeout() {
        return Duration.ofMillis(deliveryTimeoutMs);
    }

    private Instant completionDate(UUID id) {
        Map<String, Object> row = publication(id);
        Timestamp date = (Timestamp) row.get("completion_date");
        return date == null ? null : date.toInstant();
    }

    private String status(UUID id) {
        return (String) publication(id).get("status");
    }

    private Map<String, Object> publication(UUID id) {
        return jdbc.queryForMap(
                "SELECT status, completion_date FROM platform.event_publication WHERE serialized_event LIKE ?",
                like(id));
    }

    private int count(String sql, Object arg) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arg);
        return count == null ? 0 : count;
    }

    private static String like(UUID id) {
        return "%" + id + "%";
    }

    /** Freezes the broker (docker pause): it keeps its data but answers nothing. */
    private static void pause(KafkaContainer kafka) {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    private static void unpause(KafkaContainer kafka) {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }
}
