package edu.campus.starter.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

/**
 * Describes our error JSON once (schema {@code Problem}, the same fields as {@code ProblemBody})
 * and says for every endpoint: "an error answers with a Problem". The frontend then gets one
 * {@code Problem} TypeScript type for all errors.
 *
 * <p>In the OpenAPI JSON it looks like:
 * <pre>{@code
 * "responses": {
 *   "200": {...},
 *   "default": {"description": "Error",
 *               "content": {"application/problem+json":
 *                           {"schema": {"$ref": "#/components/schemas/Problem"}}}}}
 * }</pre>
 * {@code default} = "any status not listed above" (OpenAPI rule).
 *
 * <p>A {@link GlobalOpenApiCustomizer} runs for every group (every module), not just the default one.
 */
public class ProblemSchemaCustomizer implements GlobalOpenApiCustomizer {

    static final String PROBLEM = "Problem";
    static final String FIELD_ERROR = "FieldError";
    static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSchemas(PROBLEM, problemSchema());
        openApi.getComponents().addSchemas(FIELD_ERROR, fieldErrorSchema());

        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::addDefaultErrorResponse));
    }

    private void addDefaultErrorResponse(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        // Keep a "default" the controller wrote itself; add ours only when there is none.
        operation
                .getResponses()
                .computeIfAbsent(
                        "default",
                        key -> new ApiResponse()
                                .description("Error (problem details, see the Problem schema)")
                                .content(new Content()
                                        .addMediaType(
                                                PROBLEM_JSON,
                                                new MediaType()
                                                        .schema(new Schema<>()
                                                                .$ref("#/components/schemas/" + PROBLEM)))));
    }

    /** Same fields as edu.campus.starter.problem.ProblemBody. */
    private static Schema<?> problemSchema() {
        Schema<?> schema = new ObjectSchema()
                .description("Error response (RFC 7807 problem details + code and traceId)")
                .addProperty(
                        "type",
                        new StringSchema()
                                .format("uri")
                                .description("Kind of problem, e.g. https://campus.edu/problems/not-found"))
                .addProperty(
                        "title", new StringSchema().description("Short summary, same for every problem of this kind"))
                .addProperty("status", new IntegerSchema().description("HTTP status code"))
                .addProperty("detail", new StringSchema().description("What went wrong this time"))
                .addProperty(
                        "code", new StringSchema().description("Stable machine code, e.g. NOT_FOUND. Switch on this."))
                .addProperty("traceId", new StringSchema().description("Id to find this request in the logs"))
                .addProperty(
                        "errors",
                        new ArraySchema()
                                .items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR))
                                .description("Only for VALIDATION_FAILED: one entry per invalid field"));
        schema.setRequired(List.of("type", "title", "status", "detail", "code", "traceId"));
        return schema;
    }

    private static Schema<?> fieldErrorSchema() {
        Schema<?> schema = new ObjectSchema()
                .description("One invalid field of a request")
                .addProperty("field", new StringSchema().description("Field name, e.g. name"))
                .addProperty("message", new StringSchema().description("What is wrong, e.g. must not be blank"));
        schema.setRequired(List.of("field", "message"));
        return schema;
    }
}
