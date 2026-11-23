package edu.campus.starter.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();
    private final MdcTraceIdProvider provider = new MdcTraceIdProvider();

    @Test
    void theIdIsInTheMdcDuringTheRequestAndInTheHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInside = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> seenInside.set(provider.traceId()));

        assertThat(seenInside.get()).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(seenInside.get());
    }

    @Test
    void theMdcIsClearedAfterTheRequest_soAPooledThreadDoesNotReuseTheId() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void outsideARequestTheProviderStillGivesAnId() {
        assertThat(provider.traceId()).matches("[0-9a-f]{32}");
    }
}
