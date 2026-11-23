package edu.campus.platform;

import edu.campus.CoreApplication;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * One OpenAPI group per Modulith module (spec A1, T9.2): {@code /v3/api-docs/platform},
 * later {@code /v3/api-docs/course}, {@code /v3/api-docs/grade}, ... Swagger UI shows them in a
 * drop-down list.
 *
 * <p>The list of modules comes from Modulith ({@link ApplicationModules}), the same list that
 * {@code ModularityTest} checks. So a new module gets its own group with no code change here.
 *
 * <p>How: springdoc makes one group per {@link GroupedOpenApi}. We cannot write one {@code @Bean}
 * method per module (we do not know the modules when writing this code), so we give springdoc
 * one bean that is a <b>list</b> of groups. springdoc supports this form.
 */
@Configuration(proxyBeanMethods = false)
public class ModuleApiGroups {

    /** Bean name differs from this class's own bean name ("moduleApiGroups"), so they don't clash. */
    @Bean
    List<GroupedOpenApi> openApiGroupPerModule() {
        return groupsFor(ApplicationModules.of(CoreApplication.class));
    }

    /**
     * A group per module: named after the module ("platform"), showing only the endpoints in the
     * module's package ({@code edu.campus.platform} and below). Sorted by name.
     */
    public static List<GroupedOpenApi> groupsFor(ApplicationModules modules) {
        return modules.stream()
                .map(ModuleApiGroups::groupFor)
                .sorted(Comparator.comparing(GroupedOpenApi::getGroup))
                .toList();
    }

    private static GroupedOpenApi groupFor(ApplicationModule module) {
        return GroupedOpenApi.builder()
                .group(module.getIdentifier().toString())
                .displayName(module.getDisplayName())
                .packagesToScan(module.getBasePackage().getName())
                .build();
    }
}
