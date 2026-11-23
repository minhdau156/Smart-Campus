package edu.campus.starter.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Temporary trace id (T8.5, replaced in T16): runs first for every request and
 * <ol>
 *   <li>makes up a new id: 32 hex characters, the same format as a W3C/OpenTelemetry trace id;
 *   <li>puts it in the logging MDC under {@code traceId}, so log lines of this request can show it;
 *   <li>sends it back in the {@code X-Trace-Id} response header, also when there is no error.
 * </ol>
 * MDC ("mapped diagnostic context") = a per-thread map that the logger can print with every line.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = newTraceId();
        MDC.put(MDC_KEY, traceId);
        // Set before the controller runs: once the body is written, headers can no longer be added.
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // the thread goes back to the pool and must not keep this id
        }
    }

    /** A random UUID without its dashes = 32 lowercase hex characters. */
    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
