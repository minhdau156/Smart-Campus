package edu.campus.kernel.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Test clocks. Production code takes a {@link Clock} and never calls {@code Instant.now()} (spec
 * code style), so tests can pass one of these and get the same time on every run.
 *
 * <p>Shared with other modules through the shared-kernel test-jar:
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>edu.campus</groupId>
 *     <artifactId>shared-kernel</artifactId>
 *     <version>${project.version}</version>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 * }</pre>
 */
public final class Clocks {

    private Clocks() {}

    /** A clock that is always at this instant, in UTC. Example: {@code fixedAt("2026-09-07T08:00:00Z")}. */
    public static Clock fixedAt(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    /** A clock that is always {@code offset} ahead of {@code base} (use a negative offset for the past). */
    public static Clock offset(Clock base, Duration offset) {
        return Clock.offset(base, offset);
    }
}
