package edu.campus.kernel.error;

import java.net.URI;
import java.util.Locale;

/**
 * Stable, machine-readable error codes. Each one maps to an RFC 7807 problem response: its HTTP
 * status, a short title, and a {@code type} URI ({@code https://campus.edu/problems/<code>}).
 * Clients switch on the code, never on the message text.
 */
public enum ProblemCode {
    INTERNAL_ERROR(500, "Internal error"),
    VALIDATION_FAILED(400, "Validation failed"),
    NOT_FOUND(404, "Not found"),
    CONFLICT(409, "Conflict"),
    RATE_LIMITED(429, "Rate limited");

    private static final String TYPE_BASE = "https://campus.edu/problems/";

    private final int status;
    private final String title;
    private final URI type;

    ProblemCode(int status, String title) {
        this.status = status;
        this.title = title;
        this.type = URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** HTTP status code, e.g. 404. */
    public int status() {
        return status;
    }

    /** Short, human-readable summary that is the same for every occurrence, e.g. "Not found". */
    public String title() {
        return title;
    }

    /** RFC 7807 {@code type}, e.g. {@code https://campus.edu/problems/not-found}. */
    public URI type() {
        return type;
    }
}
