package edu.campus.kernel.error;

import java.util.Objects;

/**
 * Throw this to end a request with a problem response. The web layer (T8) turns it into RFC 7807
 * JSON with the code's status, title and type, plus this {@code detail}:
 *
 * <pre>{@code
 * throw new CampusProblem(ProblemCode.NOT_FOUND, "Course %s does not exist".formatted(id));
 * }</pre>
 */
public class CampusProblem extends RuntimeException {

    private final ProblemCode code;

    public CampusProblem(ProblemCode code, String detail) {
        this(code, detail, null);
    }

    public CampusProblem(ProblemCode code, String detail, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ProblemCode code() {
        return code;
    }

    /** Explanation for this occurrence, e.g. "Course CS101 does not exist". Shown to the client. */
    public String detail() {
        return getMessage();
    }
}
