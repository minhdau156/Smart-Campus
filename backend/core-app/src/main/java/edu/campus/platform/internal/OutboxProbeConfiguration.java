package edu.campus.platform.internal;

import edu.campus.kernel.event.DomainEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Dev/test only (T7 outbox spike): which events go to Kafka, and where.
 *
 * <p>T12 replaces this with the real table for all topics (shared-kernel {@code Topics}).
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "test"})
class OutboxProbeConfiguration {

    /**
     * Tells Modulith: externalize probe envelopes (and nothing else) to {@link ProbeTopic#NAME},
     * with the key computed by {@link ProbeTopic#route}.
     */
    @Bean
    EventExternalizationConfiguration eventExternalization() {
        return EventExternalizationConfiguration.externalizing()
                .select(ProbeTopic::isProbeEvent)
                .route(DomainEvent.class, ProbeTopic::route)
                .build();
    }

    /**
     * Creates the topic at startup (KafkaAdmin does this for every NewTopic bean). The broker does
     * not create topics by itself (auto-create is off in infra/docker-compose.yml).
     */
    @Bean
    NewTopic probeTopic() {
        return TopicBuilder.name(ProbeTopic.NAME)
                .partitions(ProbeTopic.PARTITIONS)
                .replicas(1)
                .build();
    }
}
