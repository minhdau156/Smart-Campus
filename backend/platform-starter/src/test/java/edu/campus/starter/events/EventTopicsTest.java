package edu.campus.starter.events;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.kernel.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EventTopicsTest {

    @ParameterizedTest
    @EnumSource(Topics.class)
    void aTopicIsDeclaredWithItsNamePartitionsAndUnlimitedRetention(Topics topic) {
        NewTopic declared = EventTopics.declare(topic);

        assertThat(declared.name()).isEqualTo(topic.topicName());
        assertThat(declared.numPartitions()).isEqualTo(topic.partitions());
        // Event topics keep every event forever, so read models can be rebuilt (spec D5).
        assertThat(declared.configs()).containsEntry("retention.ms", "-1");
    }
}
