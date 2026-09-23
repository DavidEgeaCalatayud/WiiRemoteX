pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WiiRemoteX"
include(
    ":app",
    ":core:model",
    ":core:protocol",
    ":core:session",
    ":platform:bluetooth",
    ":platform:sensors",
    ":transports:esp32-ble",
    ":feature:controller",
    ":shared",
)
