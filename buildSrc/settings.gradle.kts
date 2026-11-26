// Google's Central mirror ahead of the plugin portal, which redirects kotlin-dsl's
// dependencies to Central; see dependencyResolutionManagement in the root settings.gradle.kts.
pluginManagement {
    repositories {
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
