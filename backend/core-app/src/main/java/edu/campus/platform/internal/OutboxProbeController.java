package edu.campus.platform.internal;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev/test only (T7 outbox spike): lets a developer try the outbox by hand.
 * {@code curl -X POST "localhost:8081/api/v1/platform/outbox-probe?note=hi"} creates a probe; its
 * event then shows up on the Kafka topic {@code platform.probe}.
 */
@Hidden // a dev tool, not part of the API: left out of the OpenAPI docs (T9)
@RestController
@Profile({"dev", "test"})
class OutboxProbeController {

    private final OutboxProbeService probes;

    OutboxProbeController(OutboxProbeService probes) {
        this.probes = probes;
    }

    @PostMapping("/api/v1/platform/outbox-probe")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> create(@RequestParam(defaultValue = "manual probe") String note) {
        return Map.of("id", probes.create(note));
    }
}
