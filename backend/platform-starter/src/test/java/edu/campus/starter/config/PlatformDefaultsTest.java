package edu.campus.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PlatformDefaultsTest {

    private final PlatformDefaultsEnvironmentPostProcessor defaults = new PlatformDefaultsEnvironmentPostProcessor();

    private StandardEnvironment environmentWith(Map<String, Object> appProperties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("app", appProperties));
        defaults.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    void addsTheOutboxDefaults() {
        StandardEnvironment environment = environmentWith(Map.of("spring.flyway.default-schema", "platform"));

        assertThat(environment.getProperty("spring.modulith.events.jdbc.schema-initialization.enabled"))
                .isEqualTo("false");
        assertThat(environment.getProperty("spring.modulith.events.republish-outstanding-events-on-restart"))
                .isEqualTo("true");
        assertThat(environment.getProperty("spring.kafka.producer.acks")).isEqualTo("all");
        assertThat(environment.getProperty("campus.outbox.retry-interval")).isEqualTo("30s");
    }

    @Test
    void theOutboxTableIsInTheAppsOwnFlywaySchema() {
        // core-app -> platform; registration-service -> registration (T10), ...
        assertThat(environmentWith(Map.of("spring.flyway.default-schema", "platform"))
                        .getProperty("spring.modulith.events.jdbc.schema"))
                .isEqualTo("platform");
        assertThat(environmentWith(Map.of("spring.flyway.default-schema", "registration"))
                        .getProperty("spring.modulith.events.jdbc.schema"))
                .isEqualTo("registration");
    }

    @Test
    void theAppsOwnSettingsWin() {
        StandardEnvironment environment = environmentWith(Map.of("campus.outbox.retry-interval", "2s"));

        assertThat(environment.getProperty("campus.outbox.retry-interval")).isEqualTo("2s");
    }
}
