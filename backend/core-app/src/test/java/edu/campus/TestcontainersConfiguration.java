package edu.campus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL in Docker for integration tests. {@code @ServiceConnection} gives its URL,
 * user and password to Spring, so no datasource settings are needed in the {@code test} profile.
 * The image is the same one as in infra/docker-compose.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:19.0-alpine3.24");
    }
}
