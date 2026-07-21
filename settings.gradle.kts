import org.gradle.api.initialization.ProjectDescriptor

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dagger.hilt.android.plugin") {
                useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://maven.fpregistry.io/releases")
        maven(url = "https://jitpack.io")
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "Flipcash"

include(
    // app containers
    ":apps:flipcash:app",
    ":apps:flipcash:benchmark",

    // flipcash modules
    ":apps:flipcash:core",
    ":libs:test-utils",
    // shared flipcash coordinators/controllers/viewmodels/services
    ":apps:flipcash:shared:amount-entry",
    ":apps:flipcash:shared:accesskey",
    ":apps:flipcash:shared:analytics",
    ":apps:flipcash:shared:appsettings",
    ":apps:flipcash:shared:authentication",
    ":apps:flipcash:shared:activityfeed",
    ":apps:flipcash:shared:bills",
    ":apps:flipcash:shared:bill-customization",
    ":apps:flipcash:shared:chat",
    ":apps:flipcash:shared:chat-ui",
    ":apps:flipcash:shared:contacts",
    ":apps:flipcash:shared:currency-creator",
    ":apps:flipcash:shared:onramp:coinbase",
    ":apps:flipcash:shared:onramp:deeplinks",
    ":apps:flipcash:shared:appupdates",
    ":apps:flipcash:shared:google-play-billing",
    ":apps:flipcash:shared:region-selection:core",
    ":apps:flipcash:shared:region-selection:ui",
    ":apps:flipcash:shared:featureflags",
    ":apps:flipcash:shared:ksp",
    ":apps:flipcash:shared:menu",
    ":apps:flipcash:shared:notifications",
    ":apps:flipcash:shared:payments",
    ":apps:flipcash:shared:permissions",
    ":apps:flipcash:shared:push",
    ":apps:flipcash:shared:persistence:db",
    ":apps:flipcash:shared:persistence:provider",
    ":apps:flipcash:shared:persistence:sources",
    ":apps:flipcash:shared:phone",
    ":apps:flipcash:shared:router",
    ":apps:flipcash:shared:session",
    ":apps:flipcash:shared:shareable",
    ":apps:flipcash:shared:invite",
    ":apps:flipcash:shared:tokens",
    ":apps:flipcash:shared:tokens:core",
    ":apps:flipcash:shared:theme",
    ":apps:flipcash:shared:profile",
    ":apps:flipcash:shared:blob",
    ":apps:flipcash:shared:userflags",
    ":apps:flipcash:shared:workers",
    ":apps:flipcash:shared:web",
    // flipcash features
    ":apps:flipcash:features:login",
    ":apps:flipcash:features:scanner",
    ":apps:flipcash:features:cash",
    ":apps:flipcash:features:balance",
    ":apps:flipcash:features:menu",
    ":apps:flipcash:features:purchase",
    ":apps:flipcash:features:lab",
    ":apps:flipcash:features:appsettings",
    ":apps:flipcash:features:appupdates",
    ":apps:flipcash:features:deposit",
    ":apps:flipcash:features:advanced",
    ":apps:flipcash:features:currency-creator",
    ":apps:flipcash:features:direct-send",
    ":apps:flipcash:features:messenger",
    ":apps:flipcash:features:invite",
    ":apps:flipcash:features:device-logs",
    ":apps:flipcash:features:myaccount",
    ":apps:flipcash:features:backupkey",
    ":apps:flipcash:features:shareapp",
    ":apps:flipcash:features:withdrawal",
    ":apps:flipcash:features:contact-verification",
    ":apps:flipcash:features:tokens",
    ":apps:flipcash:features:transactions",
    ":apps:flipcash:features:bill-customization",
    ":apps:flipcash:features:discovery",
    ":apps:flipcash:features:userflags",

    // protobuf model and service implementations for the Open Code Protocol
    ":definitions:opencode:models",
    ":definitions:opencode:protos",

    // protobuf model and service implementations for Flipcash
    ":definitions:flipcash:models",
    ":definitions:flipcash:protos",

    // Internal libs
    ":libs:analytics",
    ":libs:biometrics",
    ":libs:code-detection",
    ":libs:coroutines",
    ":libs:crypto:kin",
    ":libs:crypto:solana",
    ":libs:currency",
    ":libs:currency-math",
    ":libs:datetime",
    ":libs:emojis",
    ":libs:encryption:base58",
    ":libs:encryption:ed25519",
    ":libs:encryption:hmac",
    ":libs:encryption:keys",
    ":libs:encryption:mnemonic",
    ":libs:encryption:sha256",
    ":libs:encryption:sha512",
    ":libs:encryption:utils",

    ":libs:locale:bindings",
    ":libs:locale:impl",
    ":libs:locale:public",

    ":libs:logging",
    ":libs:messaging",
    ":libs:models",
    ":libs:network:exchange",
    ":libs:opengraph",

    ":libs:network:coinbase:onramp",

    ":libs:network:connectivity:bindings",
    ":libs:network:connectivity:impl",
    ":libs:network:connectivity:public",

    ":libs:network:jwt",

    ":libs:permissions:bindings",
    ":libs:permissions:impl",
    ":libs:permissions:public",

    ":libs:quickresponse",
    ":libs:search",

    ":libs:vibrator:bindings",
    ":libs:vibrator:impl",
    ":libs:vibrator:public",

    // Services definition for app and lib access
    ":services:opencode",
    ":services:opencode-compose",
    ":services:flipcash",
    ":services:flipcash-compose",

    // common UI
    ":ui:biometrics",
    ":ui:core",
    ":ui:components",
    ":ui:emojis",
    ":ui:navigation",
    ":ui:resources",
    ":ui:scanner",
    ":ui:testing",
    ":ui:theme",

    // 3rd party imported dependencies
    ":vendor:kik:scanner",
    ":vendor:tipkit:tipkit",
    ":vendor:tipkit:tipkit-m2",
    ":vendor:opencv:sdk",
)

// ---- Cross-module aggregation wiring (single source of truth) ----
// The root build script can no longer discover modules by iterating `subprojects`
// (Isolated Projects forbids it), so the module sets used for coverage and the
// aggregate unit-test task are derived here from the actual included projects and
// handed to the root project via extra properties. New modules are picked up
// automatically; only modules that break the usual conventions need to be listed
// in the small denylists below (their plugin type isn't knowable until project
// configuration, which IP forbids the root from inspecting).
// Only real modules, not the intermediate container projects that Gradle
// auto-creates for nested paths (e.g. `:apps:flipcash:shared`) — those have no
// build script and must not receive a `kover(project(...))` / test dependency.
val includedProjectPaths = buildList {
    fun collect(descriptor: ProjectDescriptor) {
        val hasBuildScript = descriptor.projectDir.resolve("build.gradle.kts").exists() ||
            descriptor.projectDir.resolve("build.gradle").exists()
        if (descriptor.path != ":" && hasBuildScript) {
            add(descriptor.path)
        }
        descriptor.children.forEach(::collect)
    }
    collect(rootProject)
}

// Coverage: every module under these paths applies a `flipcash.android.*`
// convention plugin (which pulls in Kover); :apps:flipcash:app applies Kover
// directly. The denylist holds the modules under those paths that have no Kover.
val koverPaths = listOf(":apps:flipcash", ":libs", ":ui", ":definitions", ":services:flipcash", ":services:opencode")
val nonKoverModules = setOf(
    ":apps:flipcash:benchmark",      // com.android.test — no Kover
    ":apps:flipcash:shared:ksp",     // pure-JVM helper — no Kover
    ":definitions:opencode:protos",  // java-library proto jar — no Kover
    ":definitions:flipcash:protos",  // java-library proto jar — no Kover
)
val koverModules = includedProjectPaths.filter { path ->
    koverPaths.any { path == it || path.startsWith("$it:") } && path !in nonKoverModules
}

// Aggregate unit tests: :apps:flipcash plus the two service modules. Android
// modules expose `testDebugUnitTest`, pure-JVM modules expose `test`, and the
// androidTest `:benchmark` module has no unit-test task.
val unitTestPaths = listOf(":apps:flipcash", ":services:flipcash", ":services:opencode")
val jvmUnitTestModules = setOf(":apps:flipcash:shared:ksp")
val noUnitTestModules = setOf(":apps:flipcash:benchmark")
val unitTestCandidates = includedProjectPaths.filter { path ->
    unitTestPaths.any { path == it || path.startsWith("$it:") } && path !in noUnitTestModules
}
val androidUnitTestModules = unitTestCandidates.filter { it !in jvmUnitTestModules }

// Forced dependency versions for the per-project configuration below. The
// version catalog isn't registered on projects yet at `beforeProject` time, so
// the coordinates are resolved here from the catalog's TOML (keeping them in sync
// with `gradle/libs.versions.toml`) and captured as plain strings.
val versionsToml = rootDir.resolve("gradle/libs.versions.toml").readText()
fun catalogVersion(key: String): String =
    Regex("""(?m)^\s*${Regex.escape(key)}\s*=\s*"([^"]+)"""")
        .find(versionsToml)?.groupValues?.get(1)
        ?: error("Version '$key' not found in gradle/libs.versions.toml")
val kotlinVersion = catalogVersion("kotlin")
val serializationVersion = catalogVersion("kotlinx-serialization")

// Per-project configuration that previously lived in the root build script's
// `allprojects { }` block, plus handing the aggregation lists above to the root
// project. Isolated Projects forbids a project (the root) configuring other
// projects, so this runs via the isolated `gradle.lifecycle.beforeProject` hook,
// which applies per-project setup to every project (including the root) without
// crossing project boundaries.
//
// The action is isolated (serialized), so it must not capture a reference to this
// settings script. Everything it needs is therefore copied into local `val`s in
// the block below — captured by value rather than via the script object.
run {
    val koverModulesForRoot = koverModules.toList()
    val androidUnitTestForRoot = androidUnitTestModules.toList()
    val jvmUnitTestForRoot = jvmUnitTestModules.toList()
    val forcedDependencies = listOf(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion",
        "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion",
    )
    gradle.lifecycle.beforeProject {
        if (path == ":") {
            extra["flipcash.koverModules"] = koverModulesForRoot
            extra["flipcash.androidUnitTestModules"] = androidUnitTestForRoot
            extra["flipcash.jvmUnitTestModules"] = jvmUnitTestForRoot
        }

        configurations.configureEach {
            exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-jdk7"))
            resolutionStrategy.force(*forcedDependencies.toTypedArray())
        }
        tasks.matching { task -> task.name.contains("kapt") }.configureEach {
            enabled = false
        }
    }
}
