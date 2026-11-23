package edu.campus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * T8.6: outbox and Modulith settings live in platform-starter, the same for every app. core-app's
 * own application.yml must not set them again (except the {@code test} profile, which makes
 * timeouts short for the tests).
 */
class NoOwnPlatformConfigTest {

    @Test
    void coreAppDoesNotSetOutboxOrModulithSettingsItself() throws IOException {
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader().load("core-app", new ClassPathResource("application.yml"));

        List<String> ownPlatformKeys = new ArrayList<>();
        for (PropertySource<?> document : documents) {
            if ("test".equals(document.getProperty("spring.config.activate.on-profile"))) {
                continue;
            }
            for (String key : ((EnumerablePropertySource<?>) document).getPropertyNames()) {
                if (key.startsWith("spring.modulith.")
                        || key.startsWith("campus.outbox.")
                        || key.startsWith("spring.kafka.producer.")) {
                    ownPlatformKeys.add(key);
                }
            }
        }

        assertThat(ownPlatformKeys).as("set these in platform-starter instead").isEmpty();
    }

    @Test
    void coreAppHasNoOutboxOrModuleDetectionClassesOfItsOwn() {
        assertThat(classExists("edu.campus.CoreModuleDetection")).isFalse();
        assertThat(classExists("edu.campus.platform.internal.OutboxRetryJob")).isFalse();
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
