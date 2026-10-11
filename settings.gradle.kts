// Root build settings for the Ambient Brightness Controller.
// Three modules: the application shell, the Android-free domain core and the
// Android infrastructure adapters.
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

rootProject.name = "AmbientBrightnessController"

include(":app", ":core", ":infrastructure")
