package edu.campus.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exports the OpenAPI docs of notification-service to {@code docs/api/notification-service.json},
 * so the frontend can generate TypeScript types from them (spec A1). Same rules as core-app's
 * OpenApiExportIT, but this app is not split into Modulith modules, so it has one document.
 *
 * <ul>
 *   <li>{@code ./mvnw verify -Pexport-openapi}: (re)writes the file. Commit the diff.
 *   <li>a normal {@code ./mvnw verify}: only compares, and fails if the committed file is out of date.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiExportIT {

    /** Set to "true" by the export-openapi Maven profile (root pom.xml). */
    private static final boolean EXPORT = Boolean.getBoolean("openapi.export");

    /** Failsafe runs in backend/notification-service, so the repo root is two folders up. */
    private static final Path FILE =
            Path.of("../../docs/api/notification-service.json").toAbsolutePath().normalize();

    /** Sorted keys + pretty printing = the same text for the same API, every time. */
    private static final JsonMapper SORTED = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @LocalServerPort
    private int port;

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void docsListPingAndPointErrorsToTheSharedProblemSchema() throws Exception {
        JsonNode doc = SORTED.readTree(get("/v3/api-docs").body());

        assertThat(doc.get("paths").propertyNames()).contains("/api/v1/notification/ping");
        assertThat(doc.at("/components/schemas/Problem/properties").propertyNames())
                .contains("code", "traceId");
        assertThat(doc.at("/paths/~1api~1v1~1notification~1ping/get/responses/default/content")
                        .at("/application~1problem+json/schema/$ref")
                        .asString())
                .isEqualTo("#/components/schemas/Problem");
    }

    @Test
    void exportedFileMatchesTheRunningApp() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        String json = normalize(response.body());

        if (EXPORT) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json, StandardCharsets.UTF_8);
        } else {
            assertThat(FILE)
                    .as("%s is missing. Run ./mvnw verify -Pexport-openapi and commit it.", FILE)
                    .exists();
            assertThat(Files.readString(FILE, StandardCharsets.UTF_8))
                    .as("%s is out of date. Run ./mvnw verify -Pexport-openapi and commit the diff.", FILE)
                    .isEqualTo(json);
        }
    }

    /** Sorted keys, 2-space indent, "\n" line ends (also on Windows), one newline at the end. */
    static String normalize(String json) {
        Map<?, ?> tree = SORTED.readValue(json, Map.class);
        return SORTED.writeValueAsString(tree).replace("\r\n", "\n") + "\n";
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
