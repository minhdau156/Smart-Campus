package edu.campus;

import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/**
 * How Spring Modulith finds the modules of core-app (set in application.yml with
 * {@code spring.modulith.detection-strategy}).
 *
 * <p>Same as Modulith's default (every package directly below {@code edu.campus} is a module), with
 * one exception: {@code edu.campus.kernel}. Those classes come from the shared-kernel jar. It is a
 * plain library that every module may use (ids, Money, DomainEvent, Topics), not a module of this
 * app. Without this, Modulith would treat it as a module and block access to its sub-packages.
 */
public class CoreModuleDetection implements ApplicationModuleDetectionStrategy {

    static final String SHARED_KERNEL = "edu.campus.kernel";

    private final ApplicationModuleDetectionStrategy defaults = ApplicationModuleDetectionStrategy.directSubPackage();

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage rootPackage) {
        return defaults.getModuleBasePackages(rootPackage)
                .filter(pkg -> !pkg.getName().equals(SHARED_KERNEL));
    }
}
