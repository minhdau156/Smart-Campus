/**
 * The "platform" module: shared technical things for core-app (ping now; outbox and topics later).
 * Its public API is this package. Anything in a sub-package such as {@code platform.internal} is
 * private to the module, and {@code ModularityTest} fails if another module uses it.
 */
@ApplicationModule(displayName = "Platform")
package edu.campus.platform;

import org.springframework.modulith.ApplicationModule;
