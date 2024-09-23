buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        mavenCentral()
    }

    dependencies {
        classpath(Classpath.android_gradle_build_tools)
        classpath(Classpath.androidx_navigation_safeargs)
        classpath(Classpath.kotlin_gradle_plugin)
        classpath(Classpath.google_services)
        classpath(Classpath.crashlytics_gradle)
        classpath(Classpath.bugsnag)
        classpath(Classpath.firebase_perf)
        classpath(Classpath.secrets_gradle_plugin)
        classpath(Classpath.kotlin_serialization_plugin)
        classpath(Classpath.protobuf_plugin)
        classpath(Classpath.versioning_gradle_plugin)
    }
}

plugins {
    id(Plugins.kotlin_ksp) version Versions.kotlin_ksp apply false
    id(Plugins.hilt) version Versions.hilt apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://maven.fpregistry.io/releases")
        maven(url = "https://jitpack.io")
    }
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }

    subprojects {
        subprojects.onEach { subproject ->
            subproject.tasks.whenTaskAdded {
                if (name.contains("kapt")) {
                    enabled = false
                }
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
