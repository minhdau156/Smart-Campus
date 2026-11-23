package edu.campus.starter.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Adds {@code campus-platform-defaults.yml} (in this jar) to every app's settings, at the
 * <b>lowest</b> priority ({@code addLast}): an app's own application.yml, environment variables,
 * and command-line arguments all win over it.
 *
 * <p>Spring Boot calls this very early, before the app's beans exist. It is registered in
 * {@code META-INF/spring.factories} (key {@code org.springframework.boot.EnvironmentPostProcessor}).
 * Like Maven's parent pom, but for application settings: shared defaults in one place.
 */
public class PlatformDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String FILE = "campus-platform-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("platform-starter defaults", new ClassPathResource(FILE));
            sources.forEach(environment.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + FILE + " from platform-starter", e);
        }
    }
}
