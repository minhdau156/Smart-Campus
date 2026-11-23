package edu.campus.starter.trace;

import org.slf4j.MDC;

/**
 * Reads the id that {@link TraceIdFilter} put in the MDC. Outside a request (no filter ran, e.g.
 * a scheduled job) it makes up a new one, so a trace id is always there.
 */
public class MdcTraceIdProvider implements TraceIdProvider {

    @Override
    public String traceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return traceId != null ? traceId : TraceIdFilter.newTraceId();
    }
}
