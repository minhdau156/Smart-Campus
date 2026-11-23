package edu.campus.starter.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.List;

/**
 * The body of every error response (RFC 7807 "problem details" + our {@code code} and
 * {@code traceId}). Jackson writes the fields in this order:
 *
 * <pre>{@code
 * {"type":"https://campus.edu/problems/conflict","title":"Conflict","status":409,
 *  "detail":"Section S1 has no seats left","code":"CONFLICT","traceId":"4bf92f35..."}
 * }</pre>
 *
 * @param type    a URI naming the kind of problem (clients may show it as a help link)
 * @param title   short summary, the same for every problem of this kind
 * @param status  the HTTP status, repeated in the body
 * @param detail  what went wrong this time (never internal details for a 500)
 * @param code    stable machine code; clients switch on this, never on the text
 * @param traceId id to find this request in the logs
 * @param errors  only for VALIDATION_FAILED: one entry per bad field; left out otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemBody(
        URI type, String title, int status, String detail, String code, String traceId, List<FieldError> errors) {

    /** One invalid field, e.g. {@code {"field":"name","message":"must not be blank"}}. */
    public record FieldError(String field, String message) {}
}
