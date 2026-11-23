package edu.campus.starter.problem;

import edu.campus.starter.trace.MdcTraceIdProvider;
import edu.campus.starter.trace.TraceIdFilter;
import edu.campus.starter.trace.TraceIdProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Problem JSON + trace id for Spring MVC apps (core-app, registration-service,
 * notification-service). Listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so an app
 * gets it just by having platform-starter on its classpath: no annotation, no import.
 *
 * <p>{@code @ConditionalOnWebApplication(type = SERVLET)}: only in Spring MVC apps. The gateway
 * (WebFlux, T13) does not get these beans.
 *
 * <p>Also listed in {@code META-INF/spring/...AutoConfigureWebMvc.imports}, so a controller test
 * with {@code @WebMvcTest} gets the same error JSON as the running app.
 *
 * <p>Note: an app whose main class is in {@code edu.campus} (core-app) also finds
 * {@link CampusProblemHandler} by component scan, because {@code edu.campus.starter} is below it.
 * {@code @ConditionalOnMissingBean} then skips the second copy here, so there is always exactly one.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProblemHandlingAutoConfiguration {

    /** Where the trace id comes from. {@code @ConditionalOnMissingBean}: T16 can bring its own. */
    @Bean
    @ConditionalOnMissingBean
    TraceIdProvider traceIdProvider() {
        return new MdcTraceIdProvider();
    }

    /** Runs the trace id filter first, before any other filter, for every request. */
    @Bean
    FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    CampusProblemHandler campusProblemHandler(TraceIdProvider traceIds) {
        return new CampusProblemHandler(traceIds);
    }
}
