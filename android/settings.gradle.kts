pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "omni-os"
include(":core:design", ":core:common", ":core:network", ":core:weather", ":apps:reference", ":apps:weather", ":apps:control", ":apps:guide", ":apps:play", ":apps:gallery", ":core:library", ":apps:cinema", ":apps:server")
