package edu.campus.notification;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link Clock} of the app. Code asks for a Clock instead of calling {@code Instant.now()}
 * (spec code style), so tests can give a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
