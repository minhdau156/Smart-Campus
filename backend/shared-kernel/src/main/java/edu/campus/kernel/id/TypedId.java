package edu.campus.kernel.id;

import java.util.UUID;

/**
 * An id with its own type, so a {@code CourseId} can never be passed where a {@code StudentId} is
 * expected. Each module writes its ids as small records:
 *
 * <pre>{@code
 * public record StudentId(UUID value) implements TypedId {
 *     public StudentId {
 *         Ids.requireId(value, "StudentId");
 *     }
 * }
 * }</pre>
 */
public interface TypedId {

    /** The raw UUID, for storage and for the wire. */
    UUID value();
}
