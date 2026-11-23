package edu.campus.starter.problem;

import static org.assertj.core.api.Assertions.assertThat;

import edu.campus.starter.testapp.TestApp;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec E1: the 4 kinds of error all come back as the same problem JSON, with a traceId and
 * without a stack trace. Calls a real running app (the starter's {@link TestApp}) over HTTP.
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ProblemJsonIT {

    /** The exact fields of every problem body (validation adds "errors"). */
    private static final Set<String> PROBLEM_FIELDS = Set.of("type", "title", "status", "detail", "code", "traceId");

    private static final String HEX_32 = "[0-9a-f]{32}";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void campusProblem_isMappedWithItsOwnCodeStatusAndDetail() throws Exception {
        HttpResponse<String> response = send(get("/test/conflict"));
        JsonNode body = assertProblem(response, 409, "CONFLICT");

        assertThat(body.get("type").asString()).isEqualTo("https://campus.edu/problems/conflict");
        assertThat(body.get("title").asString()).isEqualTo("Conflict");
        assertThat(body.get("detail").asString()).isEqualTo("Section S1 has no seats left");
        assertThat(fieldNames(body)).isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void validationFailure_is400WithOneEntryPerBadField() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/test/courses"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"\",\"credits\":99}"))
                .build());
        JsonNode body = assertProblem(response, 400, "VALIDATION_FAILED");

        assertThat(body.get("type").asString()).isEqualTo("https://campus.edu/problems/validation-failed");
        Set<String> expected = new HashSet<>(PROBLEM_FIELDS);
        expected.add("errors");
        assertThat(fieldNames(body)).isEqualTo(expected);
        assertThat(body.get("errors")
                        .valueStream()
                        .map(error -> error.get("field").asString()))
                .containsExactlyInAnyOrder("name", "credits");
        assertThat(body.get("errors")
                        .valueStream()
                        .map(error -> error.get("message").asString()))
                .allSatisfy(message -> assertThat(message).isNotBlank());
    }

    @Test
    void unknownRoute_is404NotFound() throws Exception {
        HttpResponse<String> response = send(get("/api/v1/does-not-exist"));
        JsonNode body = assertProblem(response, 404, "NOT_FOUND");

        assertThat(body.get("detail").asString()).contains("/api/v1/does-not-exist");
        assertThat(fieldNames(body)).isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void unhandledException_is500WithAGenericDetailAndIsLoggedWithTheTraceId(CapturedOutput log) throws Exception {
        HttpResponse<String> response = send(get("/test/boom"));
        JsonNode body = assertProblem(response, 500, "INTERNAL_ERROR");

        // Nothing from the exception reaches the client: no message, no class name, no stack trace.
        assertThat(response.body())
                .doesNotContain("hunter2")
                .doesNotContain("IllegalStateException")
                .doesNotContain("\tat ")
                .doesNotContain("trace\"");
        assertThat(body.get("detail").asString()).contains(body.get("traceId").asString());
        assertThat(fieldNames(body)).isEqualTo(PROBLEM_FIELDS);

        // ...but the full error IS in the server log, next to the same traceId.
        String traceId = body.get("traceId").asString();
        assertThat(log.getAll())
                .contains("traceId=" + traceId)
                .contains("IllegalStateException: db password is hunter2");
    }

    @Test
    void otherSpringErrors_keepSpringsStatusAndGetACodeFromIt() throws Exception {
        // POST to a GET-only endpoint: Spring says 405. There is no ProblemCode for 405, so the code
        // is the status name, and Spring's "Allow" header is kept.
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/test/ok"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
        JsonNode body = assertProblem(response, 405, "METHOD_NOT_ALLOWED");

        assertThat(body.get("type").asString()).isEqualTo("https://campus.edu/problems/method-not-allowed");
        assertThat(response.headers().firstValue("Allow"))
                .hasValueSatisfying(allow -> assertThat(allow).contains("GET"));
        assertThat(fieldNames(body)).isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void everyResponseHasItsOwnTraceIdHeader_alsoWithoutError() throws Exception {
        HttpResponse<String> first = send(get("/test/ok"));
        HttpResponse<String> second = send(get("/test/ok"));

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("X-Trace-Id"))
                .hasValueSatisfying(id -> assertThat(id).matches(HEX_32));
        assertThat(first.headers().firstValue("X-Trace-Id"))
                .isNotEqualTo(second.headers().firstValue("X-Trace-Id"));
    }

    // ----- helpers -----

    /** Checks what every problem response has in common, and returns the parsed body. */
    private JsonNode assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));

        JsonNode body = json.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asString()).isEqualTo(code);
        assertThat(body.get("traceId").asString()).matches(HEX_32);
        // The body's traceId is the same one the response header carries.
        assertThat(response.headers().firstValue("X-Trace-Id"))
                .hasValue(body.get("traceId").asString());
        return body;
    }

    private static Set<String> fieldNames(JsonNode body) {
        return new HashSet<>(body.propertyNames());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(uri(path)).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
