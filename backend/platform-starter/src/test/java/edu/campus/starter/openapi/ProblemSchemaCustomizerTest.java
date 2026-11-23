package edu.campus.starter.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class ProblemSchemaCustomizerTest {

    private final ProblemSchemaCustomizer customizer = new ProblemSchemaCustomizer();

    private static OpenAPI apiWithOneGetOperation() {
        Operation get = new Operation()
                .operationId("ping")
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
        return new OpenAPI().paths(new Paths().addPathItem("/ping", new PathItem().get(get)));
    }

    @Test
    void addsTheSharedProblemSchemaWithCodeAndTraceId() {
        OpenAPI api = apiWithOneGetOperation();

        customizer.customise(api);

        Schema<?> problem = api.getComponents().getSchemas().get("Problem");
        assertThat(problem.getProperties())
                .containsOnlyKeys("type", "title", "status", "detail", "code", "traceId", "errors");
        assertThat(problem.getRequired())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "code", "traceId");
        assertThat(api.getComponents().getSchemas()).containsKey("FieldError");
    }

    @Test
    void everyOperationGetsADefaultErrorResponseThatPointsToProblem() {
        OpenAPI api = apiWithOneGetOperation();

        customizer.customise(api);

        ApiResponses responses = api.getPaths().get("/ping").getGet().getResponses();
        assertThat(responses).containsKeys("200", "default"); // the success response is kept
        Schema<?> schema = responses
                .get("default")
                .getContent()
                .get("application/problem+json")
                .getSchema();
        assertThat(schema.get$ref()).isEqualTo("#/components/schemas/Problem");
    }

    @Test
    void aDefaultResponseWrittenByTheControllerIsKept() {
        OpenAPI api = apiWithOneGetOperation();
        ApiResponse own = new ApiResponse().description("my own");
        api.getPaths().get("/ping").getGet().getResponses().addApiResponse("default", own);

        customizer.customise(api);

        assertThat(api.getPaths().get("/ping").getGet().getResponses().get("default"))
                .isSameAs(own);
    }
}
