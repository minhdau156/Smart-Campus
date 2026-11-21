package edu.campus.kernel.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainEventTest {

    private static final UUID EVENT_ID = UUID.fromString("0b9f6c2e-4d1a-4c3b-9a57-2f1e8d7c6b5a");
    private static final Instant AT = Instant.parse("2026-09-07T08:00:00Z");

    record SeatTaken(String sectionId) {}

    private static final SeatTaken PAYLOAD = new SeatTaken("section-1234");

    @Test
    void validEventKeepsAllFields() {
        DomainEvent<SeatTaken> event = new DomainEvent<>(EVENT_ID, "SeatTaken", 1, AT, "section-1234", PAYLOAD);

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.type()).isEqualTo("SeatTaken");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(AT);
        assertThat(event.aggregateId()).isEqualTo("section-1234");
        assertThat(event.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    void nullEventIdIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainEvent<>(null, "SeatTaken", 1, AT, "section-1234", PAYLOAD))
                .withMessageContaining("eventId");
    }

    @Test
    void nullTypeIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, null, 1, AT, "section-1234", PAYLOAD))
                .withMessageContaining("type");
    }

    @Test
    void nullOccurredAtIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, "SeatTaken", 1, null, "section-1234", PAYLOAD))
                .withMessageContaining("occurredAt");
    }

    @Test
    void nullAggregateIdIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, "SeatTaken", 1, AT, null, PAYLOAD))
                .withMessageContaining("aggregateId");
    }

    @Test
    void nullPayloadIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, "SeatTaken", 1, AT, "section-1234", null))
                .withMessageContaining("payload");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void schemaVersionBelowOneIsRejected(int version) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, "SeatTaken", version, AT, "section-1234", PAYLOAD))
                .withMessageContaining("schemaVersion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankTypeIsRejected(String type) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, type, 1, AT, "section-1234", PAYLOAD))
                .withMessageContaining("type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankAggregateIdIsRejected(String aggregateId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DomainEvent<>(EVENT_ID, "SeatTaken", 1, AT, aggregateId, PAYLOAD))
                .withMessageContaining("aggregateId");
    }
}
