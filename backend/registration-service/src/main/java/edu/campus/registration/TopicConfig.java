package edu.campus.registration;

import edu.campus.kernel.event.Topics;
import edu.campus.starter.events.EventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Kafka topics that registration-service produces (spec O5): seat requests (12 partitions,
 * keyed by section) and registration results (6 partitions, keyed by student). Spring's
 * {@code KafkaAdmin} creates them at startup with {@code retention.ms=-1}. The broker never creates
 * a topic by itself.
 */
@Configuration(proxyBeanMethods = false)
class TopicConfig {

    @Bean
    NewTopic registrationRequestsTopic() {
        return EventTopics.declare(Topics.REGISTRATION_REQUESTS);
    }

    @Bean
    NewTopic registrationEventsTopic() {
        return EventTopics.declare(Topics.REGISTRATION_EVENTS);
    }
}
