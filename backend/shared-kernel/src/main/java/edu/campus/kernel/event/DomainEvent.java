package edu.campus.kernel.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The envelope around every event sent between modules (Arch §6 envelope + {@code schemaVersion},
 * spec D4). The JSON form is fixed by {@code src/test/resources/golden/domain-event.json}.
 *
 * @param eventId unique per event; consumers use it to skip duplicates (spec D2)
 * @param type past tense, PascalCase, e.g. {@code "EnrollmentConfirmed"}
 * @param schemaVersion starts at 1; bump it on a breaking payload change
 * @param occurredAt when it happened (take it from a {@link java.time.Clock})
 * @param aggregateId the thing that changed, e.g. {@code "section-1234"}. Not automatically the
 *     Kafka key: see {@link Topics} (spec D6)
 * @param payload ids and the changed facts, never whole entities of another module
 * @param <P> the payload type, usually a record
 */
public record DomainEvent<P>(
        UUID eventId, String type, int schemaVersion, Instant occurredAt, String aggregateId, P payload) {

    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, but got " + schemaVersion);
        }
    }
}
