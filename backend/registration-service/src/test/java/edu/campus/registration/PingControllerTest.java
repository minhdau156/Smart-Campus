package edu.campus.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Tests only the web layer for the ping endpoint: no database, no Docker. */
@WebMvcTest(PingController.class)
class PingControllerTest {

    /** Replaces the real clock, so the time in the answer is always the same. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Test
    void pingReturnsAppVersionAndTime() {
        var result = mvc.get().uri("/api/v1/registration/ping").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.app").isEqualTo("registration-service");
        assertThat(result).bodyJson().extractingPath("$.time").isEqualTo("2026-09-07T08:00:00Z");
        // Copied from pom.xml by Maven resource filtering: "@project.version@" -> "0.1.0-SNAPSHOT".
        assertThat(result)
                .bodyJson()
                .extractingPath("$.version")
                .asString()
                .isNotBlank()
                .doesNotContain("@");
    }

    /** platform-starter's error handling is active here too. */
    @Test
    void wrongMethodGivesProblemJson() {
        var result = mvc.post().uri("/api/v1/registration/ping").exchange();

        assertThat(result).hasStatus(405);
        assertThat(result).hasContentType("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }
}
