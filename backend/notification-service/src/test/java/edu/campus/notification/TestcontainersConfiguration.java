package edu.campus.notification;

import java.nio.file.Path;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Real infrastructure in Docker for the integration tests, set up like Compose.
 *
 * <ul>
 *   <li><b>PostgreSQL</b> runs the same init script as Compose
 *       ({@code infra/postgres/init/01-schemas-and-users.sh}), and the app logs in as
 *       {@code notification_svc}, the restricted user it uses in Compose. No
 *       {@code @ServiceConnection} for it: that would log in as the superuser.
 *   <li><b>Kafka</b>: the same image as Compose, connected with {@code @ServiceConnection}.
 *   <li>The {@code platform.probe} topic. In Compose, core-app (the producer) creates it.
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Test-only password, given to the init script through NOTIFICATION_SVC_DB_PASSWORD. */
    static final String NOTIFICATION_SVC_PASSWORD = "notification-svc-test";

    /** Failsafe runs in backend/notification-service, so the repo root is two folders up. */
    private static final Path INIT_SCRIPT = Path.of("../../infra/postgres/init/01-schemas-and-users.sh")
            .toAbsolutePath()
            .normalize();

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:19.0-alpine3.24")
                .withDatabaseName("campus")
                // The Postgres image runs every script in this folder once, on first start.
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT, 0755),
                        "/docker-entrypoint-initdb.d/01-schemas-and-users.sh")
                // The passwords the init script reads (in Compose they come from infra/.env).
                .withEnv("CORE_APP_DB_PASSWORD", "core-app-test")
                .withEnv("REGISTRATION_SVC_DB_PASSWORD", "registration-svc-test")
                .withEnv("NOTIFICATION_SVC_DB_PASSWORD", NOTIFICATION_SVC_PASSWORD)
                .withEnv("KEYCLOAK_DB_PASSWORD", "keycloak-test");
    }

    /** The app's datasource: the container's address, but the notification_svc login. */
    @Bean
    DynamicPropertyRegistrar notificationSvcLogin(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "notification_svc");
            registry.add("spring.datasource.password", () -> NOTIFICATION_SVC_PASSWORD);
        };
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.4.0");
    }

    /** Same name and partitions as core-app's probe topic. */
    @Bean
    NewTopic probeTopic() {
        return TopicBuilder.name("platform.probe").partitions(3).replicas(1).build();
    }
}
