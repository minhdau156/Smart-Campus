package edu.campus.starter.trace;

/**
 * Gives the trace id of the current request: the id that ties together one request's error
 * response, its log lines and (from T16) its trace in Tempo.
 *
 * <p>Today: {@link MdcTraceIdProvider}, an id made up per request by {@link TraceIdFilter}.
 * T16 replaces the bean with one that returns the real OpenTelemetry trace id.
 */
public interface TraceIdProvider {

    /** The current trace id: 32 lowercase hex characters, never null. */
    String traceId();
}
