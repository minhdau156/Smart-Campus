pluginManagement {
    // Our convention plugins: campus.java-conventions, campus.integration-test, campus.spring-app
    includeBuild("build-logic")
}

rootProject.name = "smart-campus"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include("backend:shared-kernel")
