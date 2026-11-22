package edu.campus.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Proves the boundary checks really catch a violation (spec P3). The fixture in
 * {@code fixture.boundary} has module "alpha" using a class from "beta.internal".
 *
 * <p>The fixture lives in test sources, and Modulith skips test classes by default, so here we
 * import it with an option that includes everything.
 */
class BoundaryViolationTest {

    private static final String FIXTURE = "fixture.boundary";
    private static final ImportOption INCLUDE_TEST_CLASSES = location -> true;

    @Test
    void modulithVerifyFailsWhenAModuleUsesAnotherModulesInternalPackage() {
        ApplicationModules fixtureModules = ApplicationModules.of(FIXTURE, INCLUDE_TEST_CLASSES);

        assertThatThrownBy(fixtureModules::verify)
                .isInstanceOf(Violations.class)
                .hasMessageContaining("BetaSecret");
    }

    @Test
    void archUnitRuleFailsForTheFixture() {
        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(FIXTURE);

        assertThatThrownBy(() -> InternalPackageRule.noAccessToOtherModulesInternals(FIXTURE)
                        .check(fixtureClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AlphaService")
                .hasMessageContaining("BetaSecret")
                // Using beta's public API (BetaApi) is allowed, so it is not reported.
                .hasMessageNotContaining("BetaApi");
    }

    @Test
    void archUnitRulePassesForCoreApp() {
        JavaClasses appClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("edu.campus");

        assertThatCode(() -> InternalPackageRule.noAccessToOtherModulesInternals("edu.campus")
                        .check(appClasses))
                .doesNotThrowAnyException();
    }
}
