package edu.campus.starter.modulith;

import java.util.Set;
import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/**
 * How Spring Modulith finds the modules of an app (moved from core-app's CoreModuleDetection in
 * T8.6).
 *
 * <p>Same as Modulith's default (every package directly below {@code edu.campus} is a module),
 * except our two shared libraries. They are used by every module, they are not modules:
 * <ul>
 *   <li>{@code edu.campus.kernel}: shared-kernel (ids, Money, DomainEvent, Topics, ...);
 *   <li>{@code edu.campus.starter}: this platform-starter (problem JSON, outbox setup).
 * </ul>
 *
 * <p>Registered in this jar's {@code META-INF/spring.factories}. Modulith uses that entry when an
 * app does not set {@code spring.modulith.detection-strategy}, so apps need no setting.
 */
public class CampusModuleDetection implements ApplicationModuleDetectionStrategy {

    static final Set<String> LIBRARIES = Set.of("edu.campus.kernel", "edu.campus.starter");

    private final ApplicationModuleDetectionStrategy defaults = ApplicationModuleDetectionStrategy.directSubPackage();

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage rootPackage) {
        return defaults.getModuleBasePackages(rootPackage).filter(pkg -> !LIBRARIES.contains(pkg.getName()));
    }
}
