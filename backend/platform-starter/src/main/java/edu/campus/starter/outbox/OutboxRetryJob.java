package edu.campus.starter.outbox;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The "on a schedule" part of spec D1: every {@code campus.outbox.retry-interval}, send again the
 * events whose delivery to Kafka FAILED (for example because the broker was down).
 *
 * <p>Only FAILED publications are picked, never ones still in flight (PUBLISHED / PROCESSING), and
 * Modulith marks each one RESUBMITTED before sending it again. So this job does not create a second
 * copy of an event that is still waiting for Kafka's answer. Publications stuck in flight (the app
 * died mid-send) are turned into FAILED by Modulith's staleness check
 * ({@code spring.modulith.events.staleness.*}) and then picked up here too.
 *
 * <p>Moved from core-app in T8.6; created by {@link OutboxAutoConfiguration}.
 */
public class OutboxRetryJob {

    private final FailedEventPublications failed;

    public OutboxRetryJob(FailedEventPublications failed) {
        this.failed = failed;
    }

    @Scheduled(
            initialDelayString = "${campus.outbox.retry-interval}",
            fixedDelayString = "${campus.outbox.retry-interval}")
    public void resubmitFailed() {
        failed.resubmit(ResubmissionOptions.defaults());
    }
}
