package edu.campus.registration;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/registration/ping}: a simple "I am alive" answer with the app's name and version. */
@RestController
@RequestMapping("/api/v1/registration")
class PingController {

    /** The JSON answer, e.g. {@code {"app":"registration-service","version":"0.1.0-SNAPSHOT","time":"2026-...Z"}}. */
    record Ping(String app, String version, Instant time) {}

    private final Clock clock;
    private final String app;
    private final String version;

    PingController(
            Clock clock, @Value("${spring.application.name}") String app, @Value("${campus.version}") String version) {
        this.clock = clock;
        this.app = app;
        this.version = version;
    }

    @GetMapping("/ping")
    Ping ping() {
        return new Ping(app, version, clock.instant());
    }
}
