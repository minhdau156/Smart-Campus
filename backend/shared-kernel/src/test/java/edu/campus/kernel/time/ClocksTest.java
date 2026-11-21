package edu.campus.kernel.time;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.kernel.testing.Clocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClocksTest {

    @Test
    void fixedClockAlwaysGivesTheSameInstantInUtc() {
        Clock clock = Clocks.fixedAt("2026-09-07T08:00:00Z");

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-07T08:00:00Z"));
        assertThat(clock.instant()).isEqualTo(clock.instant());
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void offsetClockIsShiftedFromItsBase() {
        Clock base = Clocks.fixedAt("2026-09-07T08:00:00Z");

        Clock twoHoursLater = Clocks.offset(base, Duration.ofHours(2));

        assertThat(twoHoursLater.instant()).isEqualTo(Instant.parse("2026-09-07T10:00:00Z"));
    }

    @Test
    void fixedClockWorksWithTimeRange() {
        Clock clock = Clocks.fixedAt("2026-09-07T08:00:00Z");

        TimeRange nextHour = new TimeRange(clock.instant(), clock.instant().plus(Duration.ofHours(1)));

        assertThat(nextHour.overlaps(
                        new TimeRange(Instant.parse("2026-09-07T08:30:00Z"), Instant.parse("2026-09-07T08:45:00Z"))))
                .isTrue();
    }
}
