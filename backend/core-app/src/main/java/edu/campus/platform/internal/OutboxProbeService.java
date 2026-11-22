package edu.campus.platform.internal;

import edu.campus.kernel.event.DomainEvent;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/test only (T7 outbox spike). Saves a business row AND publishes an event in one transaction,
 * the pattern every feature module will use later.
 *
 * <p>What happens on {@link #create}:
 * <ol>
 *   <li>INSERT into {@code platform.outbox_probe};
 *   <li>{@code publishEvent(...)}: Modulith writes the event to {@code platform.event_publication}
 *       (same transaction, same database connection);
 *   <li>COMMIT: both rows are saved, or neither is;
 *   <li>after the commit, Modulith sends the event to Kafka and marks it complete when Kafka
 *       acknowledges. If the transaction rolls back, step 4 never happens.
 * </ol>
 */
@Service
@Profile({"dev", "test"})
class OutboxProbeService {

    static final String EVENT_TYPE = "OutboxProbeCreated";

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OutboxProbeService(JdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /** Creates a probe and publishes {@code OutboxProbeCreated}; returns the probe id. */
    @Transactional
    UUID create(String note) {
        Instant now = clock.instant();
        OutboxProbe probe = new OutboxProbe(UUID.randomUUID(), note, now);

        jdbc.update(
                "INSERT INTO platform.outbox_probe (id, note, created_at) VALUES (?, ?, ?)",
                probe.id(),
                probe.note(),
                Timestamp.from(probe.createdAt()));

        events.publishEvent(new DomainEvent<>(
                UUID.randomUUID(), EVENT_TYPE, 1, now, probe.id().toString(), probe));

        return probe.id();
    }
}
