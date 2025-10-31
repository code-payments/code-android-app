buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        google()
        maven(url = "https://repo.gradle.org/gradle/libs-releases")
    }

    dependencies {
        classpath(Classpath.android_gradle_build_tools)
        classpath(Classpath.kotlin_hilt_plugin)
        classpath(Classpath.androidx_navigation_safeargs)
        classpath(Classpath.kotlin_gradle_plugin)
        classpath(Classpath.google_services)
        classpath(Classpath.crashlytics_gradle)
        classpath(Classpath.bugsnag_android_gradle_plugin)
        classpath(Classpath.bugsnag_gradle_plugin)
        classpath(Classpath.firebase_perf)
        classpath(Classpath.secrets_gradle_plugin)
        classpath(Classpath.kotlin_serialization_plugin)
        classpath(Classpath.protobuf_plugin)
        classpath(Classpath.versioning_gradle_plugin)
        classpath(Classpath.androidx_room_gradle_plugin)
        classpath("com.ahasbini.tools:android-opencv-gradle-plugin:0.1.3-dev")
    }
}

plugins {
    id(Plugins.kotlin_ksp) version Versions.kotlin_ksp apply false
    id(Plugins.jetbrains_compose_compiler) version Versions.kotlin apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://maven.fpregistry.io/releases")
        maven(url = "https://jitpack.io")
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        resolutionStrategy {
            force(Libs.kotlinx_serialization_core)
            force(Libs.kotlinx_serialization_json)
            force(Libs.protobuf_java)
        }
    }

    tasks.matching { it.name.contains("kapt") }.configureEach {
        enabled = false
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}