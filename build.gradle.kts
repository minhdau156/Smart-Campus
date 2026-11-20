// Root build: only declares plugin versions (from the catalog) for subprojects. No other logic.
plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless) apply false
}
