package edu.campus.registration;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * A real PostgreSQL in Docker, set up exactly like the Compose one: it runs the same init script
 * ({@code infra/postgres/init/01-schemas-and-users.sh}), so it has the same schemas, DB users and
 * privileges.
 *
 * <p>The app then logs in as {@code registration_svc}, the restricted user it uses in Compose, and
 * not as the container's superuser. So every IT also proves that Flyway, the outbox and the
 * endpoints work with only the rights on the {@code registration} schema (spec D3). That is why
 * there is no {@code @ServiceConnection} here: it would log in as the superuser.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Test-only password, given to the init script through REGISTRATION_SVC_DB_PASSWORD. */
    static final String REGISTRATION_SVC_PASSWORD = "registration-svc-test";

    /** Failsafe runs in backend/registration-service, so the repo root is two folders up. */
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
                .withEnv("REGISTRATION_SVC_DB_PASSWORD", REGISTRATION_SVC_PASSWORD)
                .withEnv("NOTIFICATION_SVC_DB_PASSWORD", "notification-svc-test")
                .withEnv("KEYCLOAK_DB_PASSWORD", "keycloak-test");
    }

    /** The app's datasource: the container's address, but the registration_svc login. */
    @Bean
    DynamicPropertyRegistrar registrationSvcLogin(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "registration_svc");
            registry.add("spring.datasource.password", () -> REGISTRATION_SVC_PASSWORD);
        };
    }
}
