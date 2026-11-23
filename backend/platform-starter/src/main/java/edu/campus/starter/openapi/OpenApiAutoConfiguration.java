package edu.campus.starter.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Shared OpenAPI setup for every app that has springdoc (spec A1, T9). The app adds its springdoc
 * flavor itself (core-app: springdoc-openapi-starter-webmvc-ui).
 */
@AutoConfiguration
@ConditionalOnClass(GlobalOpenApiCustomizer.class)
public class OpenApiAutoConfiguration {

    /** The shared Problem schema + "default" error response on every endpoint. */
    @Bean
    @ConditionalOnMissingBean
    ProblemSchemaCustomizer problemSchemaCustomizer() {
        return new ProblemSchemaCustomizer();
    }

    /**
     * Title and version of the doc, and a <b>relative</b> server URL ("/"). Without it springdoc
     * writes the URL of the request (e.g. http://localhost:54321), which changes with every test
     * run and differs behind the gateway. "/" = "the same host this doc was loaded from".
     */
    @Bean
    @ConditionalOnMissingBean
    OpenAPI campusOpenApi(
            @Value("${spring.application.name:app}") String appName,
            @Value("${campus.version:unknown}") String version) {
        return new OpenAPI()
                .info(new Info().title("Smart Campus " + appName).version(version))
                .servers(List.of(new Server().url("/").description("This server")));
    }
}
