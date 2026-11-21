package edu.campus.kernel.event;

import edu.campus.kernel.id.TypedId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;

/**
 * The Kafka topics from Arch §6, with the message key each one uses (spec D6). Kafka keeps order
 * only per key, so the key decides which events stay in order: e.g. all requests for one section
 * (the waitlist), or all events of one student.
 *
 * <p>Changing a name, partition count or key is a spec change (spec "Ask first").
 */
public enum Topics {
    /** Seat requests. Not replayable: a request is a command, running it twice would act twice. */
    REGISTRATION_REQUESTS("registration.requests", 12, "sectionId", false),
    REGISTRATION_EVENTS("registration.events", 6, "studentId", true),
    COURSE_EVENTS("course.events", 3, "sectionId", true),
    GRADE_EVENTS("grade.events", 3, "studentId", true),
    CAMPUS_EVENTS("campus.events", 3, "aggregateId", true);

    /** The key name that means "use the envelope's aggregateId" instead of a payload field. */
    private static final String AGGREGATE_ID = "aggregateId";

    private final String topicName;
    private final int partitions;
    private final String keyField;
    private final boolean replayable;

    Topics(String topicName, int partitions, String keyField, boolean replayable) {
        this.topicName = topicName;
        this.partitions = partitions;
        this.keyField = keyField;
        this.replayable = replayable;
    }

    /** The topic name in Kafka, e.g. {@code "registration.events"}. */
    public String topicName() {
        return topicName;
    }

    public int partitions() {
        return partitions;
    }

    /**
     * Where the key comes from: {@code "aggregateId"} means the envelope's {@code aggregateId};
     * any other name is a field of the payload record.
     */
    public String keyField() {
        return keyField;
    }

    /** True if a read-model consumer may rebuild itself by reading this topic again (spec D5). */
    public boolean replayable() {
        return replayable;
    }

    /**
     * The Kafka message key for this event on this topic. A typed id gives its UUID as text.
     *
     * @throws IllegalArgumentException if the payload has no such field, or its value is null: a
     *     missing key must fail the publication (spec D6)
     */
    public String keyOf(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event");
        if (keyField.equals(AGGREGATE_ID)) {
            return event.aggregateId();
        }
        Object value = readPayloadField(event.payload());
        if (value == null) {
            throw new IllegalArgumentException(
                    "Topic %s needs a key, but payload field '%s' is null".formatted(topicName, keyField));
        }
        return value instanceof TypedId id ? id.value().toString() : value.toString();
    }

    /** Finds a topic by its Kafka name. */
    public static Topics byName(String topicName) {
        return Arrays.stream(values())
                .filter(t -> t.topicName.equals(topicName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown topic '%s'".formatted(topicName)));
    }

    private Object readPayloadField(Object payload) {
        RecordComponent[] components = payload.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException(
                    "Topic %s reads the key from the payload, so the payload must be a record, but got %s"
                            .formatted(topicName, payload.getClass().getName()));
        }
        RecordComponent component = Arrays.stream(components)
                .filter(c -> c.getName().equals(keyField))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Topic %s needs key field '%s', but %s has no such field"
                                .formatted(
                                        topicName, keyField, payload.getClass().getSimpleName())));
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(payload);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Could not read key field '%s' of %s"
                            .formatted(keyField, payload.getClass().getName()),
                    e);
        }
    }
}
