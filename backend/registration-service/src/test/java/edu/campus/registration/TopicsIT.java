package edu.campus.registration;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.kernel.event.Topics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Spec O5 for registration-service: after startup, the topics it produces exist with their
 * partition count and unlimited retention. The test broker does not auto-create topics, so the
 * service created them. (core-app has its own TopicsIT for the other three topics.)
 *
 * <p>Same annotations as {@code RegistrationApplicationIT}, so Spring reuses one started app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TopicsIT {

    private static final List<Topics> REGISTRATION_TOPICS =
            List.of(Topics.REGISTRATION_REQUESTS, Topics.REGISTRATION_EVENTS);

    @Autowired
    private KafkaContainer kafka;

    @Test
    void registrationTopicsExistWithTheirPartitionsAndUnlimitedRetention() throws Exception {
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            List<String> names =
                    REGISTRATION_TOPICS.stream().map(Topics::topicName).toList();
            Map<String, TopicDescription> described =
                    admin.describeTopics(names).allTopicNames().get();

            for (Topics topic : REGISTRATION_TOPICS) {
                // registration.requests 12, registration.events 6 (Arch §6).
                assertThat(described.get(topic.topicName()).partitions())
                        .as(topic.topicName())
                        .hasSize(topic.partitions());
                ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic.topicName());
                Config config =
                        admin.describeConfigs(List.of(resource)).all().get().get(resource);
                assertThat(config.get("retention.ms").value())
                        .as(topic.topicName())
                        .isEqualTo("-1");
            }
        }
    }

    @Test
    void registrationDoesNotCreateCoreAppTopics() throws Exception {
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get();

            assertThat(topics).doesNotContain("course.events", "grade.events", "campus.events");
        }
    }
}
