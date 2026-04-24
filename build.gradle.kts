buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.gradle.org/gradle/libs-releases")
    }

    dependencies {
        // plugins that lack standard plugin markers
        classpath("com.ahasbini.tools:android-opencv-gradle-plugin:0.1.3-dev")
        // needed at configuration time by :libs:emojis build script
        classpath(libs.kotlinx.serialization.json)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.bugsnag.android) apply false
    alias(libs.plugins.bugsnag.gradle) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.versioning) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.kover)
}

allprojects {
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        resolutionStrategy {
            force(libs.kotlinx.serialization.core.get().toString())
            force(libs.kotlinx.serialization.json.get().toString())
        }
    }

    tasks.matching { it.name.contains("kapt") }.configureEach {
        enabled = false
    }
}

dependencies {
    subprojects.forEach { subproject ->
        subproject.afterEvaluate {
            if (subproject.plugins.hasPlugin("org.jetbrains.kotlinx.kover")
                && (subproject.path.startsWith(":apps:flipcash")
                    || subproject.path.startsWith(":services:flipcash")
                    || subproject.path.startsWith(":services:opencode")
                    || subproject.path.startsWith(":libs:")
                    || subproject.path.startsWith(":ui:")
                    || subproject.path.startsWith(":definitions:"))
            ) {
                kover(subproject)
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
