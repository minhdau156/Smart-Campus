package edu.campus.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Spec D5 / O6: inside core-app, a module may handle only its OWN events in-process. Events of
 * other modules must be consumed from Kafka, so they can be replayed.
 *
 * <p>The fixture in {@code fixture.listener} has module "alpha" with event {@code AlphaCreated},
 * and module "beta" with listeners that break the rule in four ways, and some that are allowed.
 */
class KafkaOnlyCrossModuleRuleTest {

    private static final String FIXTURE = "fixture.listener";

    @Test
    void theRuleFailsForEveryInProcessListenerOnAnotherModulesEvent() {
        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(FIXTURE);

        assertThatThrownBy(() -> KafkaOnlyCrossModuleRule.inProcessListenersOnlyForOwnEvents(FIXTURE)
                        .check(fixtureClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BetaListensToAlphaDirectly")
                .hasMessageContaining("BetaListensToAlphaEnvelope")
                .hasMessageContaining("BetaListensToAlphaByClasses")
                .hasMessageContaining("BetaListensToAnyEnvelope")
                // Own events and Spring's events are fine.
                .hasMessageNotContaining("AlphaOwnListener")
                .hasMessageNotContaining("BetaAllowedListener");
    }

    @Test
    void theRulePassesForCoreApp() {
        JavaClasses appClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("edu.campus");

        // platform-starter's EventKeyCheck listens to every envelope, but it is a library, not a
        // module, so the rule does not apply to it.
        assertThatCode(() -> KafkaOnlyCrossModuleRule.inProcessListenersOnlyForOwnEvents("edu.campus")
                        .check(appClasses))
                .doesNotThrowAnyException();
    }
}
