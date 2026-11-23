package edu.campus;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.platform.ModuleApiGroups;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exports the OpenAPI docs of core-app to {@code docs/api/core-app-<group>.json} (one file per
 * Modulith module), so the frontend can generate TypeScript types from them (spec A1).
 *
 * <ul>
 *   <li>{@code ./mvnw verify -Pexport-openapi}: (re)writes the files. Commit the diff.
 *   <li>a normal {@code ./mvnw verify}: only compares. It fails if a committed file is out of date,
 *       so nobody forgets to export after changing an endpoint.
 * </ul>
 *
 * <p>Output is deterministic: keys sorted, pretty printed, "\n" line ends, relative server URL.
 * Two runs give identical files.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiExportIT {

    /** Set to "true" by the export-openapi Maven profile (root pom.xml). */
    private static final boolean EXPORT = Boolean.getBoolean("openapi.export");

    /** Failsafe runs in backend/core-app, so the repo root is two folders up. */
    private static final Path DOCS_API =
            Path.of("../../docs/api").toAbsolutePath().normalize();

    /** Sorted keys + pretty printing = the same text for the same API, every time. */
    private static final JsonMapper SORTED = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @LocalServerPort
    private int port;

    private final HttpClient http =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private final List<String> groups = ModuleApiGroups.groupsFor(ApplicationModules.of(CoreApplication.class)).stream()
            .map(GroupedOpenApi::getGroup)
            .toList();

    @Test
    void swaggerUiListsOneGroupPerModule() throws Exception {
        JsonNode config = SORTED.readTree(get("/v3/api-docs/swagger-config").body());

        // One entry per module; "url" holds the group id, "name" the display name ("Platform").
        assertThat(config.get("urls").valueStream().map(url -> url.get("url").asString()))
                .containsExactlyInAnyOrderElementsOf(
                        groups.stream().map(group -> "/v3/api-docs/" + group).toList());
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(200);
    }

    @Test
    void errorResponsesPointToTheSharedProblemSchema() throws Exception {
        JsonNode doc = SORTED.readTree(get("/v3/api-docs/platform").body());

        assertThat(doc.at("/components/schemas/Problem/properties").propertyNames())
                .contains("code", "traceId");
        assertThat(doc.at("/paths/~1api~1v1~1platform~1ping/get/responses/default/content")
                        .at("/application~1problem+json/schema/$ref")
                        .asString())
                .isEqualTo("#/components/schemas/Problem");
    }

    @Test
    void devOnlyEndpointsAreNotInTheDocs() throws Exception {
        JsonNode doc = SORTED.readTree(get("/v3/api-docs/platform").body());

        assertThat(doc.get("paths").propertyNames())
                .contains("/api/v1/platform/ping")
                .doesNotContain("/api/v1/platform/outbox-probe");
    }

    @Test
    void exportedFilesMatchTheRunningApp() throws Exception {
        for (String group : groups) {
            HttpResponse<String> response = get("/v3/api-docs/" + group);
            assertThat(response.statusCode()).isEqualTo(200);
            String json = normalize(response.body());
            Path file = DOCS_API.resolve("core-app-" + group + ".json");

            if (EXPORT) {
                Files.createDirectories(DOCS_API);
                Files.writeString(file, json, StandardCharsets.UTF_8);
            } else {
                assertThat(file)
                        .as("%s is missing. Run ./mvnw verify -Pexport-openapi and commit it.", file)
                        .exists();
                assertThat(Files.readString(file, StandardCharsets.UTF_8))
                        .as("%s is out of date. Run ./mvnw verify -Pexport-openapi and commit the diff.", file)
                        .isEqualTo(json);
            }
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
