plugins {
    `kotlin-dsl`
}

repositories {
    google()
    // Google's Central mirror; see dependencyResolutionManagement in the root settings.gradle.kts.
    maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
}
