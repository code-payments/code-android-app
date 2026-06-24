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
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.kover)
}

// NOTE: The per-project `allprojects { }` configuration that used to live here
// (kotlin-stdlib-jdk7 exclusion, serialization/metadata version forcing, and
// disabling stray kapt tasks) has moved to `settings.gradle.kts` as a
// `gradle.lifecycle.beforeProject { }` hook. Isolated Projects forbids the root
// project from configuring other projects, and that hook is its isolated
// replacement.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// ---- Coverage aggregation (Kover) ----
// The set of modules whose coverage is merged is computed in `settings.gradle.kts`
// (from the actual included projects) and handed over via this extra property,
// because Isolated Projects forbids the root from discovering them by iterating
// `subprojects`. `kover(project(path))` is just a dependency edge, which IP allows.
@Suppress("UNCHECKED_CAST")
val koverModules = rootProject.extra["flipcash.koverModules"] as List<String>

dependencies {
    koverModules.forEach { kover(project(it)) }
}

// ---- Aggregate unit-test task ----
// Replaces a `subprojects { afterEvaluate { rootProject.tasks... } }` wiring that
// Isolated Projects forbids. The module lists come from `settings.gradle.kts`; the
// task depends on each module's test task by *path* — a lazy, cross-project-safe
// dependency that never configures the other project at configuration time.
// Android modules expose `testDebugUnitTest`; pure-JVM modules expose `test`.
@Suppress("UNCHECKED_CAST")
val androidUnitTestModules = rootProject.extra["flipcash.androidUnitTestModules"] as List<String>
@Suppress("UNCHECKED_CAST")
val jvmUnitTestModules = rootProject.extra["flipcash.jvmUnitTestModules"] as List<String>

tasks.register("flipcashTestDebug") {
    description = "Run testDebug for all Flipcash modules"
    dependsOn(androidUnitTestModules.map { "$it:testDebugUnitTest" })
    dependsOn(jvmUnitTestModules.map { "$it:test" })
}
