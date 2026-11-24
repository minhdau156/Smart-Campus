package edu.campus.starter.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The rules that need no database. The real behavior (one transaction, duplicates skipped) is
 * tested against PostgreSQL in notification-service's IdempotentConsumerIT.
 */
class IdempotentConsumerTest {

    private final JdbcTemplate jdbc = new JdbcTemplate();
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    @Test
    void usesTheProcessedEventsTableOfTheGivenSchema() {
        IdempotentConsumer consumer = new IdempotentConsumer(jdbc, transactions, "notification");

        assertThat(consumer.table()).isEqualTo("notification.processed_events");
    }

    /** The schema name becomes part of the SQL, so only plain lower-case names are allowed. */
    @ParameterizedTest
    @ValueSource(strings = {"", "Notification", "notification; DROP TABLE x", "a.b", "1abc", "no-dash"})
    void rejectsSchemaNamesThatAreNotPlainIdentifiers(String schema) {
        assertThatThrownBy(() -> new IdempotentConsumer(jdbc, transactions, schema))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingArguments() {
        IdempotentConsumer consumer = new IdempotentConsumer(jdbc, transactions, "notification");

        assertThatThrownBy(() -> consumer.runOnce(null, UUID.randomUUID(), () -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> consumer.runOnce("probe", null, () -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> consumer.runOnce("probe", UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
