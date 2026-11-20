// Shared rules for every Java project: Java 21, UTF-8, JUnit, formatting, coverage report.
plugins {
    java
    jacoco
    id("com.diffplug.spotless")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(name: String): String = libs.findVersion(name).get().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(version("java"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

dependencies {
    // Test library versions come from the Spring Boot BOM (no version numbers here).
    // A BOM only sets versions; it adds no Spring code to the project.
    testImplementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat(version("palantir-java-format"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
