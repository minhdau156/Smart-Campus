package edu.campus.notification;

import edu.campus.kernel.event.DomainEvent;
import edu.campus.starter.consumer.IdempotentConsumer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dev/test only. Reads the probe events that core-app's outbox sends to {@code platform.probe}
 * (T7) and saves one row per event in {@code notification.probe_read_model}. It is the example
 * consumer that later listeners copy: parse the envelope, then do the work inside
 * {@link IdempotentConsumer#runOnce}, so a repeated delivery changes nothing (spec D2, O4).
 */
@Component
@Profile({"dev", "test"})
class ProbeListener {

    /** The name stored in processed_events. Never change it: old rows would stop matching. */
    static final String CONSUMER = "notification.probe";

    /** This service's own view of the probe payload. It does not import core-app classes. */
    record ProbePayload(UUID id, String note, Instant createdAt) {}

    private static final TypeReference<DomainEvent<ProbePayload>> PROBE_EVENT = new TypeReference<>() {};

    private final IdempotentConsumer idempotent;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    ProbeListener(IdempotentConsumer idempotent, JdbcTemplate jdbc, JsonMapper json) {
        this.idempotent = idempotent;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** One Kafka record = one envelope as JSON text. A failure here makes Kafka deliver it again. */
    @KafkaListener(id = CONSUMER, groupId = CONSUMER, topics = "platform.probe")
    void on(String envelope) {
        DomainEvent<ProbePayload> event = json.readValue(envelope, PROBE_EVENT);
        idempotent.runOnce(
                CONSUMER,
                event.eventId(),
                () -> jdbc.update(
                        "INSERT INTO notification.probe_read_model (event_id, probe_id, note) VALUES (?, ?, ?)",
                        event.eventId(),
                        event.payload().id(),
                        event.payload().note()));
    }
}
