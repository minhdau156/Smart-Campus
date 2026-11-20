// Adds integration tests in src/integrationTest/java, run by their own task after the unit tests.
// `./gradlew test` = unit tests only, `./gradlew integrationTest` = ITs only, `check`/`build` = both.
plugins {
    java
}

val sourceSets = the<SourceSetContainer>()

val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// Integration tests get the same libraries as unit tests (JUnit, AssertJ, ...).
configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests (src/integrationTest/java)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTestTask)
}
