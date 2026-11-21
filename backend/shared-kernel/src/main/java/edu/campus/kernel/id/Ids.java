package edu.campus.kernel.id;

import java.util.Objects;
import java.util.UUID;

/** Helpers for {@link TypedId} records. */
public final class Ids {

    private Ids() {}

    /** A new random id (UUID version 4). */
    public static UUID newId() {
        return UUID.randomUUID();
    }

    /**
     * Null check for a typed id's compact constructor.
     *
     * @param idType the id's type name, used in the error message (e.g. {@code "StudentId"})
     */
    public static UUID requireId(UUID value, String idType) {
        return Objects.requireNonNull(value, () -> idType + " value must not be null");
    }
}
