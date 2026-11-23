package edu.campus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Checks the module rules of core-app (spec P3): no module uses another module's internal types,
 * and there are no cycles between modules. Runs without Spring or Docker, so it is fast.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(CoreApplication.class);

    @Test
    void modulesFollowTheRules() {
        modules.verify();
    }

    @Test
    void platformIsAModule() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .contains("platform");
    }

    /**
     * shared-kernel's classes are also in a package below edu.campus ({@code edu.campus.kernel}), but
     * they are a plain library that every module may use, not a module (see CampusModuleDetection
     * in platform-starter).
     */
    @Test
    void sharedKernelIsALibraryNotAModule() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .doesNotContain("kernel");
    }

    /** Same for platform-starter ({@code edu.campus.starter}): shared Spring setup, not a module. */
    @Test
    void platformStarterIsALibraryNotAModule() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .doesNotContain("starter");
    }
}
