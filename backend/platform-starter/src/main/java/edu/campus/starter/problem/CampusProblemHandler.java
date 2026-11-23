package edu.campus.starter.problem;

import edu.campus.kernel.error.CampusProblem;
import edu.campus.kernel.error.ProblemCode;
import edu.campus.starter.problem.ProblemBody.FieldError;
import edu.campus.starter.trace.TraceIdProvider;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception that leaves a controller into a {@link ProblemBody} (spec E1).
 *
 * <table>
 *   <caption>What is handled where</caption>
 *   <tr><th>Exception</th><th>Status / code</th></tr>
 *   <tr><td>{@link CampusProblem} (our own)</td><td>from its {@link ProblemCode}</td></tr>
 *   <tr><td>bean validation ({@code @Valid})</td><td>400 VALIDATION_FAILED + field list</td></tr>
 *   <tr><td>no endpoint for the URL</td><td>404 NOT_FOUND</td></tr>
 *   <tr><td>other Spring MVC errors (405, 415, bad JSON...)</td><td>Spring's status, code from it</td></tr>
 *   <tr><td>anything else</td><td>500 INTERNAL_ERROR, generic detail, full error only in the log</td></tr>
 * </table>
 *
 * <p>Extends Spring's {@link ResponseEntityExceptionHandler}, which already knows the right status
 * for each Spring MVC exception; we only change the body it writes.
 */
@RestControllerAdvice
public class CampusProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CampusProblemHandler.class);

    /** Same base as {@link ProblemCode#type()}, for statuses that have no ProblemCode. */
    private static final String TYPE_BASE = "https://campus.edu/problems/";

    private final TraceIdProvider traceIds;

    public CampusProblemHandler(TraceIdProvider traceIds) {
        this.traceIds = traceIds;
    }

    // ----- our own errors -----

    @ExceptionHandler(CampusProblem.class)
    ResponseEntity<Object> handleCampusProblem(CampusProblem problem) {
        ProblemCode code = problem.code();
        if (code.status() >= 500) {
            log.error("Campus problem {}, traceId={}", code, traceIds.traceId(), problem);
        }
        return respond(
                HttpHeaders.EMPTY,
                new ProblemBody(
                        code.type(),
                        code.title(),
                        code.status(),
                        problem.detail(),
                        code.name(),
                        traceIds.traceId(),
                        null));
    }

    // ----- anything nobody expected -----

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception exception) {
        String traceId = traceIds.traceId();
        // The full error, with its stack trace, goes to the log only. The client gets the traceId,
        // so support can find this exact log line.
        log.error("Unhandled exception, traceId={}", traceId, exception);
        ProblemCode code = ProblemCode.INTERNAL_ERROR;
        return respond(
                HttpHeaders.EMPTY,
                new ProblemBody(
                        code.type(),
                        code.title(),
                        code.status(),
                        "An unexpected error occurred. Quote traceId " + traceId + " when you report it.",
                        code.name(),
                        traceId,
                        null));
    }

    // ----- Spring MVC errors (the base class picks the status, we write the body) -----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // @Valid @RequestBody failed: one entry per invalid field.
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(headers, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // Constraints directly on method parameters failed, e.g. @RequestParam @Min(1) int page.
        List<FieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationFailed(headers, errors);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // No controller and no static file for this URL.
        ProblemCode code = ProblemCode.NOT_FOUND;
        return respond(
                headers,
                new ProblemBody(
                        code.type(),
                        code.title(),
                        code.status(),
                        "No endpoint " + describe(request),
                        code.name(),
                        traceIds.traceId(),
                        null));
    }

    /**
     * Every other Spring MVC error ends here (405 method not allowed, 415, unreadable JSON, ...).
     * Spring passes its own {@link ProblemDetail} as {@code body}; we keep its status and detail.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = body instanceof ProblemDetail problemDetail && problemDetail.getDetail() != null
                ? problemDetail.getDetail()
                : "Request failed";
        Optional<ProblemCode> known = forStatus(status.value());
        String code = known.map(Enum::name).orElseGet(() -> codeName(status));
        return respond(
                headers,
                new ProblemBody(
                        known.map(ProblemCode::type)
                                .orElseGet(() -> URI.create(TYPE_BASE
                                        + code.toLowerCase(Locale.ROOT).replace('_', '-'))),
                        known.map(ProblemCode::title).orElseGet(() -> reasonPhrase(status)),
                        status.value(),
                        detail,
                        code,
                        traceIds.traceId(),
                        null));
    }

    // ----- helpers -----

    private ResponseEntity<Object> validationFailed(HttpHeaders headers, List<FieldError> errors) {
        ProblemCode code = ProblemCode.VALIDATION_FAILED;
        return respond(
                headers,
                new ProblemBody(
                        code.type(),
                        code.title(),
                        code.status(),
                        "The request has %d invalid field(s)".formatted(errors.size()),
                        code.name(),
                        traceIds.traceId(),
                        errors));
    }

    /** Writes the body as application/problem+json, keeping headers Spring set (e.g. Allow). */
    private static ResponseEntity<Object> respond(HttpHeaders headers, ProblemBody body) {
        HttpHeaders out = new HttpHeaders();
        out.putAll(headers);
        out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(body.status()).headers(out).body(body);
    }

    /** The ProblemCode for an HTTP status, if we have one (e.g. 404 -> NOT_FOUND). */
    private static Optional<ProblemCode> forStatus(int status) {
        return Arrays.stream(ProblemCode.values())
                .filter(code -> code.status() == status)
                .findFirst();
    }

    /** Code for a status without a ProblemCode, e.g. 405 -> METHOD_NOT_ALLOWED. */
    private static String codeName(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "HTTP " + status.value();
    }

    private static String describe(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getMethod() + " " + servlet.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
