package edu.campus.notification;

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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts the whole service on a random port against a real PostgreSQL that is set up like Compose
 * (see {@link TestcontainersConfiguration}: the app logs in as notification_svc), and calls it over
 * HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationApplicationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

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
        HttpResponse<String> response = get("/api/v1/notification/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("app").asString()).isEqualTo("notification-service");
        assertThat(Instant.parse(body.get("time").asString())).isNotNull();
    }

    /** platform-starter is active: errors are problem JSON with a traceId (spec E1). */
    @Test
    void unknownRouteIsProblemJson() throws Exception {
        HttpResponse<String> response = get("/api/v1/notification/no-such-endpoint");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void flywayHistoryIsInTheNotificationSchemaWithTheBaselineRow() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT description, success FROM notification.flyway_schema_history WHERE version = '202611242000'");

        assertThat(row).containsEntry("description", "baseline").containsEntry("success", true);
    }

    /** The dedupe table (spec D2) and the dev/test probe table live in this service's own schema. */
    @Test
    void processedEventsAndProbeTablesAreInTheNotificationSchema() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'notification'"
                        + " AND table_name IN ('processed_events', 'probe_read_model')",
                Integer.class);

        assertThat(tables).isEqualTo(2);
    }
}
