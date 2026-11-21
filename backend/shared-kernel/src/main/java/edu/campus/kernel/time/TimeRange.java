package edu.campus.kernel.time;

import java.time.Instant;
import java.util.Objects;

/**
 * A span of time from {@code start} (included) to {@code end} (not included), like a class slot.
 * Because the end is not included, 08:00-10:00 and 10:00-12:00 touch but do not overlap.
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end, but got %s to %s".formatted(start, end));
        }
    }

    /** True if the two ranges share at least one instant. */
    public boolean overlaps(TimeRange other) {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
