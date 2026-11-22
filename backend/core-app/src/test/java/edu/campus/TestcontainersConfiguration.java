package edu.campus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real infrastructure in Docker for integration tests: PostgreSQL and Kafka. {@code @ServiceConnection}
 * gives their addresses (and the DB user and password) to Spring, so the {@code test} profile needs
 * no datasource or Kafka address. The images are the same ones as in infra/docker-compose.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:19.0-alpine3.24");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.4.0");
    }
}
