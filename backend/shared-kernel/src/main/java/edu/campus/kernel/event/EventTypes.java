package edu.campus.kernel.event;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an event's {@code type} name to its payload class, so a consumer reading JSON knows which
 * class to build for {@code payload}. Example:
 *
 * <pre>{@code
 * EventTypes types = new EventTypes().register("EnrollmentConfirmed", EnrollmentConfirmed.class);
 * Class<?> payloadClass = types.resolve(json.get("type"));
 * }</pre>
 *
 * <p>A name can be registered only once: two classes for one name would make the JSON ambiguous.
 */
public final class EventTypes {

    private final Map<String, Class<?>> byType = new ConcurrentHashMap<>();

    /**
     * Adds a type name.
     *
     * @return this registry, so calls can be chained
     * @throws IllegalStateException if the name is already registered
     */
    public EventTypes register(String type, Class<?> payloadClass) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payloadClass, "payloadClass");
        Class<?> existing = byType.putIfAbsent(type, payloadClass);
        if (existing != null) {
            throw new IllegalStateException(
                    "Event type '%s' is already registered to %s".formatted(type, existing.getName()));
        }
        return this;
    }

    /**
     * The payload class for this type name.
     *
     * @throws IllegalArgumentException if the name was never registered
     */
    public Class<?> resolve(String type) {
        Class<?> payloadClass = byType.get(type);
        if (payloadClass == null) {
            throw new IllegalArgumentException("Unknown event type '%s'".formatted(type));
        }
        return payloadClass;
    }

    /** True if this type name is registered. */
    public boolean isRegistered(String type) {
        return byType.containsKey(type);
    }
}
