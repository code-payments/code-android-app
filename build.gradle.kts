buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        mavenCentral()
    }

    dependencies {
        classpath(Classpath.android_gradle_build_tools)
        classpath(Classpath.kotlin_hilt_plugin)
        classpath(Classpath.androidx_navigation_safeargs)
        classpath(Classpath.kotlin_gradle_plugin)
        classpath(Classpath.google_services)
        classpath(Classpath.crashlytics_gradle)
        classpath(Classpath.bugsnag)
        classpath(Classpath.firebase_perf)
        classpath(Classpath.secrets_gradle_plugin)
        classpath(Classpath.kotlin_serialization_plugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://plugins.gradle.org/m2/") }
        maven { setUrl("https://jitpack.io") }
    }
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
