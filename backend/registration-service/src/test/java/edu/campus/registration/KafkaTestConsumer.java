package edu.campus.registration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test helper (same as core-app's): reads a whole topic from the beginning and returns the records with one key.
 *
 * <p>Each call uses a fresh consumer with no consumer group (it picks the partitions itself with
 * {@code assign}), so calls do not affect each other and every call sees the full history. That
 * makes "exactly one record with key X" a simple count.
 */
final class KafkaTestConsumer {

    private final String bootstrapServers;
    private final String topic;

    KafkaTestConsumer(String bootstrapServers, String topic) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
    }

    /** All records of the topic whose key is {@code key}, oldest first. */
    List<ConsumerRecord<String, String>> recordsWithKey(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.CLIENT_ID_CONFIG,
                "test-reader-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(10)).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);

            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            // Read until every partition has reached the end offset it had when we started.
            while (partitions.stream().anyMatch(p -> consumer.position(p) < end.get(p))) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
            return found;
        }
    }
}
