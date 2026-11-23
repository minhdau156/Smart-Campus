package edu.campus.starter.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox retry for every app that uses the Modulith event registry (spec D1). The settings
 * (schema, Kafka producer, staleness, retry interval) come from campus-platform-defaults.yml.
 *
 * <p>Switched on only when the app really has an event registry:
 * {@code @ConditionalOnClass} = Modulith's events API is on the classpath, and
 * {@code @ConditionalOnBean} = Modulith created the registry (its {@code FailedEventPublications}
 * bean). {@code afterName} makes Spring check this after Modulith's own auto-configuration, so the
 * bean already exists when the condition is checked. {@code @EnableScheduling} switches on
 * {@code @Scheduled} (a timer inside the app).
 */
@AutoConfiguration(afterName = "org.springframework.modulith.events.config.EventPublicationAutoConfiguration")
@ConditionalOnClass(FailedEventPublications.class)
@ConditionalOnBean(FailedEventPublications.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    OutboxRetryJob outboxRetryJob(FailedEventPublications failed) {
        return new OutboxRetryJob(failed);
    }
}
