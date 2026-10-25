pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "omni-os"
include(":app", ":core:design", ":core:common", ":core:network", ":core:weather", ":core:library", ":apps:cinema", ":apps:weather", ":apps:server", ":apps:gallery")
