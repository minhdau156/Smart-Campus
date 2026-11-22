package edu.campus.platform.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Dev/test only (T7 outbox spike). One row of {@code platform.outbox_probe}, and also the payload of
 * the probe event, so the test can compare what was saved with what reached Kafka.
 */
record OutboxProbe(UUID id, String note, Instant createdAt) {}
