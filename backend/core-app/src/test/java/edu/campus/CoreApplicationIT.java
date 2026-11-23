package edu.campus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts the whole app on a random port, with a real PostgreSQL (Testcontainers), and calls it over
 * HTTP like a browser would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CoreApplicationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsUpIncludingTheDatabase() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("status").asString()).isEqualTo("UP");
        assertThat(body.at("/components/db/status").asString()).isEqualTo("UP");
    }

    @Test
    void pingAnswersWithJson() throws Exception {
        HttpResponse<String> response = get("/api/v1/platform/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("app").asString()).isEqualTo("core-app");
        assertThat(Instant.parse(body.get("time").asString())).isNotNull();
    }

    @Test
    void flywayHistoryIsInThePlatformSchemaWithTheBaselineRow() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT description, success FROM platform.flyway_schema_history WHERE version = '202611220000'");

        assertThat(row).containsEntry("description", "baseline").containsEntry("success", true);
    }

    /**
     * Flyway owns every table. Modulith's own schema creation must be off: it would run
     * {@code CREATE SCHEMA}, which the core_app DB user may not do (seen in T7 against Compose; here
     * the test DB user is a superuser, so only the bean's absence can catch it).
     */
    @Test
    void modulithDoesNotCreateTablesItself() {
        assertThat(context.containsBean("databaseSchemaInitializer")).isFalse();
    }

    /** platform-starter is active in core-app: errors are problem JSON with a traceId (spec E1). */
    @Test
    void unknownRouteIsProblemJsonWithATraceId() throws Exception {
        HttpResponse<String> response = get("/api/v1/no-such-endpoint");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(body.get("traceId").asString())
                .matches("[0-9a-f]{32}")
                .isEqualTo(response.headers().firstValue("X-Trace-Id").orElseThrow());
    }

    @Test
    void everyMigrationSucceeded() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM platform.flyway_schema_history WHERE NOT success", Integer.class);

        assertThat(failed).isZero();
    }
}
