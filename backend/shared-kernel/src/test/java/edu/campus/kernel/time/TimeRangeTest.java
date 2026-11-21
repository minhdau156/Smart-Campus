package edu.campus.kernel.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(Instant.parse(start), Instant.parse(end));
    }

    // Monday 08:00-10:00 UTC
    private final TimeRange morning = range("2026-09-07T08:00:00Z", "2026-09-07T10:00:00Z");

    @Test
    void touchingRangesDoNotOverlap() {
        TimeRange next = range("2026-09-07T10:00:00Z", "2026-09-07T12:00:00Z");

        assertThat(morning.overlaps(next)).isFalse();
        assertThat(next.overlaps(morning)).isFalse();
    }

    @Test
    void partlyOverlappingRangesOverlap() {
        TimeRange later = range("2026-09-07T09:30:00Z", "2026-09-07T11:00:00Z");

        assertThat(morning.overlaps(later)).isTrue();
        assertThat(later.overlaps(morning)).isTrue();
    }

    @Test
    void nestedRangesOverlap() {
        TimeRange inside = range("2026-09-07T08:30:00Z", "2026-09-07T09:00:00Z");

        assertThat(morning.overlaps(inside)).isTrue();
        assertThat(inside.overlaps(morning)).isTrue();
    }

    @Test
    void disjointRangesDoNotOverlap() {
        TimeRange afternoon = range("2026-09-07T13:00:00Z", "2026-09-07T15:00:00Z");

        assertThat(morning.overlaps(afternoon)).isFalse();
        assertThat(afternoon.overlaps(morning)).isFalse();
    }

    @Test
    void aRangeOverlapsItself() {
        assertThat(morning.overlaps(morning)).isTrue();
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> range("2026-09-07T10:00:00Z", "2026-09-07T08:00:00Z"))
                .withMessageContaining("before");
    }

    @Test
    void emptyRangeIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> range("2026-09-07T08:00:00Z", "2026-09-07T08:00:00Z"));
    }

    @Test
    void nullStartOrEndIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TimeRange(null, Instant.EPOCH))
                .withMessageContaining("start");
        assertThatNullPointerException()
                .isThrownBy(() -> new TimeRange(Instant.EPOCH, null))
                .withMessageContaining("end");
    }

    @Test
    void overlapsRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> morning.overlaps(null));
    }
}
