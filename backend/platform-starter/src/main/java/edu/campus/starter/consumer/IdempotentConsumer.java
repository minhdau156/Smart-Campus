package edu.campus.starter.consumer;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes an event consumer idempotent: the side effect of one event runs at most once per consumer,
 * even when Kafka delivers the event two or more times (spec D2, O4).
 *
 * <p>How: in <b>one</b> database transaction, it first inserts {@code (consumer, eventId)} into
 * {@code <schema>.processed_events}, then runs the side effect. If that row already exists, the
 * event was handled before and the side effect is skipped. Both writes commit together or roll back
 * together:
 * <ul>
 *   <li>a repeat delivery finds the row and does nothing;
 *   <li>if the side effect fails, the row is rolled back too, so a retry runs it again;
 *   <li>two copies arriving at the same moment: the second insert waits for the first transaction,
 *       then sees the row (or, if the first rolled back, inserts it and runs).
 * </ul>
 *
 * <p>The side effect must write through the same DataSource (for example the app's
 * {@link JdbcTemplate}), so it joins this transaction. Effects outside the database (email, push)
 * cannot be rolled back; see the notification spec for those.
 *
 * <p>Each consuming module creates one for its own schema, because {@code processed_events} lives
 * in the module's schema:
 *
 * <pre>{@code
 * new IdempotentConsumer(jdbc, transactionManager, "notification")
 *         .runOnce("notification.probe", event.eventId(), () -> saveRow(event));
 * }</pre>
 *
 * The table: {@code processed_events(consumer TEXT, event_id UUID, processed_at TIMESTAMPTZ
 * DEFAULT now(), PRIMARY KEY (consumer, event_id))}, created by the module's Flyway migration.
 */
public class IdempotentConsumer {

    /** A plain lower-case SQL name: the schema becomes part of the SQL text, so nothing else. */
    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z][a-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String table;
    private final String insertSql;

    public IdempotentConsumer(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        Objects.requireNonNull(schema, "schema");
        if (!SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("Not a plain schema name: '%s'".formatted(schema));
        }
        this.table = schema + ".processed_events";
        // ON CONFLICT DO NOTHING: a duplicate inserts 0 rows instead of failing the transaction.
        this.insertSql = "INSERT INTO " + table + " (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
    }

    /**
     * Runs {@code sideEffect} if this consumer has not processed this event before.
     *
     * @param consumer a stable name for the consuming listener, e.g. {@code "notification.probe"}
     * @param eventId the envelope's {@code eventId}; every copy of an event has the same one
     * @param sideEffect the work to do once; an exception rolls everything back and is rethrown
     * @return true if the side effect ran now, false if the event was a duplicate and was skipped
     */
    public boolean runOnce(String consumer, UUID eventId, Runnable sideEffect) {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sideEffect, "sideEffect");

        Boolean ran = transaction.execute(status -> {
            if (jdbc.update(insertSql, consumer, eventId) == 0) {
                return false; // seen before: skip
            }
            sideEffect.run();
            return true;
        });
        return Boolean.TRUE.equals(ran);
    }

    /** The dedupe table, e.g. {@code notification.processed_events}. */
    public String table() {
        return table;
    }
}
