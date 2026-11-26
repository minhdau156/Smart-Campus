package edu.campus.starter.events;

import edu.campus.kernel.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Turns an entry of the shared {@link Topics} table into a {@link NewTopic}. An app declares the
 * topics it produces as beans, and Spring's {@code KafkaAdmin} creates them at startup (the broker
 * does not create topics by itself):
 *
 * <pre>{@code
 * @Bean
 * NewTopic gradeEvents() {
 *     return EventTopics.declare(Topics.GRADE_EVENTS);
 * }
 * }</pre>
 */
public final class EventTopics {

    /** Keep events forever: a read model can then be rebuilt from the whole history (spec D5). */
    static final String UNLIMITED = "-1";

    private EventTopics() {}

    /**
     * The topic with its partition count and {@code retention.ms=-1}. The replication factor is
     * the broker's default (1 on the single dev broker).
     */
    public static NewTopic declare(Topics topic) {
        return TopicBuilder.name(topic.topicName())
                .partitions(topic.partitions())
                .config(TopicConfig.RETENTION_MS_CONFIG, UNLIMITED)
                .build();
    }
}
