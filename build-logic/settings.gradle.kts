// Google's Central mirror ahead of Central; see dependencyResolutionManagement in the root
// settings.gradle.kts. pluginManagement is spelled out because the default (the plugin portal
// alone) redirects kotlin-dsl's dependencies to Central.
pluginManagement {
    repositories {
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
