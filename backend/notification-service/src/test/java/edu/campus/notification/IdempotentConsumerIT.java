package edu.campus.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.campus.starter.consumer.IdempotentConsumer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec D2 / O4 against a real PostgreSQL (logged in as notification_svc): platform-starter's
 * {@link IdempotentConsumer} runs a side effect once per (consumer, eventId), and the dedupe row
 * and the side effect commit or roll back together.
 *
 * <p>The side effect here is the same as the probe listener's: one row in
 * {@code notification.probe_read_model}. That table has its own id, so without the dedupe a second
 * delivery WOULD add a second row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IdempotentConsumerIT {

    private static final String CONSUMER = "it.consumer";

    @Autowired
    private IdempotentConsumer consumer;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theSameEventDeliveredTwiceRunsTheSideEffectOnce() {
        UUID eventId = UUID.randomUUID();

        boolean first = consumer.runOnce(CONSUMER, eventId, () -> saveProbeRow(eventId));
        boolean second = consumer.runOnce(CONSUMER, eventId, () -> saveProbeRow(eventId));

        assertThat(first).isTrue();
        assertThat(second).as("a repeat is skipped").isFalse();
        assertThat(probeRows(eventId)).isOne();
        assertThat(dedupeRows(eventId)).isOne();
    }

    @Test
    void aFailureAfterTheDedupeInsertRollsBackBothAndARetrySucceeds() {
        UUID eventId = UUID.randomUUID();

        // The dedupe row is inserted first, then the side effect writes its row and fails.
        assertThatThrownBy(() -> consumer.runOnce(CONSUMER, eventId, () -> {
                    saveProbeRow(eventId);
                    throw new IllegalStateException("injected failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected failure");

        assertThat(dedupeRows(eventId)).as("dedupe row rolled back").isZero();
        assertThat(probeRows(eventId)).as("side effect rolled back").isZero();

        // Kafka delivers the event again: this time it works, exactly once.
        assertThat(consumer.runOnce(CONSUMER, eventId, () -> saveProbeRow(eventId)))
                .isTrue();
        assertThat(probeRows(eventId)).isOne();
        assertThat(dedupeRows(eventId)).isOne();
    }

    /** The key is (consumer, eventId): two different listeners each handle the event once. */
    @Test
    void twoConsumersOfTheSameEventEachRunOnce() {
        UUID eventId = UUID.randomUUID();

        assertThat(consumer.runOnce("it.first", eventId, () -> saveProbeRow(eventId)))
                .isTrue();
        assertThat(consumer.runOnce("it.second", eventId, () -> saveProbeRow(eventId)))
                .isTrue();
        assertThat(consumer.runOnce("it.first", eventId, () -> saveProbeRow(eventId)))
                .isFalse();

        assertThat(probeRows(eventId)).isEqualTo(2);
    }

    /**
     * Two copies at the same moment (e.g. after a consumer-group rebalance): the second insert waits
     * for the first transaction and then sees the row, so the side effect still runs once.
     */
    @Test
    void twoCopiesAtTheSameTimeRunTheSideEffectOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> deliver = () -> {
            start.await();
            return consumer.runOnce(CONSUMER, eventId, () -> {
                saveProbeRow(eventId);
                sleep(300); // keep the transaction open, so the other copy must wait
            });
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(pool.submit(deliver), pool.submit(deliver));
            start.countDown();

            List<Boolean> ran = List.of(
                    results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
            assertThat(ran).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(probeRows(eventId)).isOne();
    }

    private void saveProbeRow(UUID eventId) {
        jdbc.update(
                "INSERT INTO notification.probe_read_model (event_id, probe_id, note) VALUES (?, ?, ?)",
                eventId,
                UUID.randomUUID(),
                "IdempotentConsumerIT");
    }

    private int probeRows(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification.probe_read_model WHERE event_id = ?", Integer.class, eventId);
    }

    private int dedupeRows(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification.processed_events WHERE event_id = ?", Integer.class, eventId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
