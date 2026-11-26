package edu.campus.platform;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.TestcontainersConfiguration;
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
 * Spec O5 for core-app: after startup, the topics it produces exist with their partition count and
 * unlimited retention. The test broker does not auto-create topics, so core-app created them.
 * (registration-service has its own TopicsIT for its two topics.)
 *
 * <p>Same annotations as {@code CoreApplicationIT}, so Spring reuses one started app for both.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TopicsIT {

    private static final List<Topics> CORE_APP_TOPICS =
            List.of(Topics.COURSE_EVENTS, Topics.GRADE_EVENTS, Topics.CAMPUS_EVENTS);

    @Autowired
    private KafkaContainer kafka;

    @Test
    void coreAppTopicsExistWithTheirPartitionsAndUnlimitedRetention() throws Exception {
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            List<String> names = CORE_APP_TOPICS.stream().map(Topics::topicName).toList();
            Map<String, TopicDescription> described =
                    admin.describeTopics(names).allTopicNames().get();

            for (Topics topic : CORE_APP_TOPICS) {
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
    void coreAppDoesNotCreateTheRegistrationTopics() throws Exception {
        try (Admin admin =
                Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get();

            // Each topic is declared by the app that produces it (spec O5).
            assertThat(topics).doesNotContain("registration.requests", "registration.events");
        }
    }
}
