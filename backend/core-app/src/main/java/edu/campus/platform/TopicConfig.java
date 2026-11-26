package edu.campus.platform;

import edu.campus.kernel.event.Topics;
import edu.campus.starter.events.EventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Kafka topics that core-app produces (spec O5). Spring's {@code KafkaAdmin} creates them at
 * startup, with the partition count from {@link Topics} and {@code retention.ms=-1} (keep every
 * event, so read models can be rebuilt by replay, spec D5). The broker never creates a topic by
 * itself. {@code registration.*} topics belong to registration-service.
 *
 * <p>The modules that publish to these topics (course, grade, ...) come later; the topics already
 * exist so that their consumers can be written against them.
 */
@Configuration(proxyBeanMethods = false)
class TopicConfig {

    @Bean
    NewTopic courseEventsTopic() {
        return EventTopics.declare(Topics.COURSE_EVENTS);
    }

    @Bean
    NewTopic gradeEventsTopic() {
        return EventTopics.declare(Topics.GRADE_EVENTS);
    }

    @Bean
    NewTopic campusEventsTopic() {
        return EventTopics.declare(Topics.CAMPUS_EVENTS);
    }
}
