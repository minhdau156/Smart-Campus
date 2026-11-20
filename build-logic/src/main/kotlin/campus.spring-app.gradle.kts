// Rules for a runnable Spring Boot app (core-app, registration-service, notification-service, gateway).
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("campus.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // Spring Boot BOM: sets the versions of all Spring and related libraries.
    val bootBom = platform(libs.findLibrary("spring-boot-dependencies").get())
    implementation(bootBom)
    annotationProcessor(bootBom)
}

// Layered jar: dependencies and our code go in separate Docker layers, so image rebuilds are fast.
tasks.named<BootJar>("bootJar") {
    layered {
        enabled = true
    }
}
