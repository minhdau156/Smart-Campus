package edu.campus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;

/**
 * Our own ArchUnit rule, next to Modulith's {@code verify()} (spec P3): a class may use another
 * module's classes only from that module's public part, never from a package named
 * {@code internal}. A module is the first package below the root, e.g. {@code edu.campus.grade}.
 */
final class InternalPackageRule {

    private InternalPackageRule() {}

    static ArchRule noAccessToOtherModulesInternals(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(notUseOtherModulesInternals(root))
                .as("classes should not use another module's internal package");
    }

    private static ArchCondition<JavaClass> notUseOtherModulesInternals(String root) {
        return new ArchCondition<>("not use another module's internal package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String ownModule = moduleOf(root, origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    boolean otherModule = !moduleOf(root, targetPackage).equals(ownModule);
                    if (otherModule && isInternal(root, targetPackage)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** The module of a package: its first segment below the root, or "" if outside a module. */
    static String moduleOf(String root, String pkg) {
        if (!pkg.startsWith(root + ".")) {
            return "";
        }
        return pkg.substring(root.length() + 1).split("\\.")[0];
    }

    /** True if the package is inside a module and has a segment named exactly "internal". */
    static boolean isInternal(String root, String pkg) {
        if (moduleOf(root, pkg).isEmpty()) {
            return false;
        }
        String[] segments = pkg.substring(root.length() + 1).split("\\.");
        return Arrays.stream(segments).skip(1).anyMatch("internal"::equals);
    }
}
