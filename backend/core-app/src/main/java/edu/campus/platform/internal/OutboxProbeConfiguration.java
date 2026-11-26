package edu.campus.platform.internal;

import edu.campus.starter.events.EventRoute;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Dev/test only (T7 outbox spike): which events go to Kafka, and where.
 *
 * <p>Since T12 the probe uses the same routing as every real event: an {@link EventRoute} bean,
 * collected by platform-starter's {@code EventRoutingAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "test"})
class OutboxProbeConfiguration {

    /** Probe envelopes go to {@link ProbeTopic#NAME}, keyed by {@link ProbeTopic#keyOf}. */
    @Bean
    EventRoute probeRoute() {
        return EventRoute.custom(OutboxProbeService.EVENT_TYPE, ProbeTopic.NAME, ProbeTopic::keyOf);
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
