// Standalone Gradle build for the Windows desktop app. Kept separate from the
// Android root project on purpose: run it with `./gradlew -p desktop <task>`
// from the repo root (reuses the root wrapper) without touching the app build.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "spotlight-desktop"
