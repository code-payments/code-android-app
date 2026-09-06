import co.touchlab.kmmbridge.KmmBridgeExtension
import org.gradle.api.tasks.bundling.Zip

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("co.touchlab.kmmbridge.github") version "1.2.1"
    `maven-publish`
}

group = "com.flipcash"

// The published version, and the tag the Swift Package repo gets. CI passes the
// release version in; the fallback only matters for local builds.
version = findProperty("sharedCoreVersion") as String? ?: "0.1.0"

// Where the published `Package.swift` lives. CI points this at a checkout of
// `code-payments/flipcash-shared-core-spm` that already holds a copy of
// `spm/`; locally it falls back to the root build directory so a publish run
// can't dirty the repo. Left unset, KMMBridge would write it to this repo's root.
val spmPackageDir = findProperty("spmRepoDir") as String?
    ?: rootProject.layout.buildDirectory.dir("spm").get().asFile.path

kotlin {
    android {
        namespace = "com.flipcash.shared"
        compileSdk = 37
        minSdk = 29
    }
    
    val appleTargets = listOf(iosArm64(), iosSimulatorArm64(), iosX64(), macosArm64(), macosX64())
    appleTargets.forEach {
        it.binaries.framework {
            baseName = "SharedCore"
            isStatic = true
            export(project(":libs:codes:kikcode"))
            export(project(":libs:encryption:base58"))
            export(project(":libs:encryption:sha256"))
            export(project(":libs:encryption:sha512"))
            export(project(":libs:encryption:hmac"))
            export(project(":libs:encryption:ed25519"))
            export(project(":libs:encryption:mnemonic"))
            export(project(":libs:currency-math:discrete-curve"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":libs:codes:kikcode"))
                api(project(":libs:encryption:base58"))
                api(project(":libs:encryption:sha256"))
                api(project(":libs:encryption:sha512"))
                api(project(":libs:encryption:hmac"))
                api(project(":libs:encryption:ed25519"))
                api(project(":libs:encryption:mnemonic"))
                api(project(":libs:currency-math:discrete-curve"))
            }
        }
    }
}

kmmbridge {
    // Both halves point at the Swift Package repo rather than this one: the
    // XCFramework zip is uploaded as a release asset there, and the generated
    // `Package.swift` that references it is committed there. iOS then depends on
    // a small public repo instead of the whole Android app.
    gitHubReleaseArtifacts(repository = "code-payments/flipcash-shared-core-spm")
    // `useCustomPackageFile` keeps `spm/Package.swift` — which adds the `SharedCoreKit`
    // Swift target over the framework — and rewrites only the variables block inside it.
    // The platform and tools version below are what KMMBridge would generate on its own;
    // with a custom file it's `spm/Package.swift` that decides, so keep the two in step.
    spm(spmDirectory = spmPackageDir, useCustomPackageFile = true, swiftToolVersion = "5.9") {
        iOS { v("15") }
        macOS { v("14") }
    }
}

// KMMBridge 1.2.1's `zipXCFramework` task (a bare Gradle `Zip` task, only registered when
// `ENABLE_PUBLISHING=true`) dereferences symlinks instead of preserving them. A macOS framework
// bundle depends entirely on symlinks — `Versions/Current -> A`, and the top-level
// Headers/Modules/Resources/binary each symlink into `Versions/Current/*` — so the archive it
// produces for the macOS slice ships three literal duplicate copies of the same content instead of
// a real `Versions/A` plus symlinks. That breaks codesign for anything that consumes the macOS
// slice (nested-bundle signing needs the real bundle layout). `matching {}.configureEach {}` is a
// no-op when the task doesn't exist (i.e. every non-publishing build), and applies once KMMBridge
// registers it otherwise, regardless of task-registration order.
tasks.withType<Zip>().matching { it.name == "zipXCFramework" }.configureEach {
    // Resolved at configuration time — `project` can't be touched inside `doLast` under the
    // configuration cache, so only plain values and the `ProviderFactory` itself cross into it.
    val buildType = project.extensions.getByType<KmmBridgeExtension>().buildType.get().getName()
    val sourceDir = project.layout.buildDirectory.dir("XCFrameworks/$buildType").get().asFile
    val execOperations = project.providers
    doLast {
        val xcframeworkDir = sourceDir.listFiles { file -> file.isDirectory && file.name.endsWith(".xcframework") }
            ?.singleOrNull()
            ?: error("Expected exactly one .xcframework under $sourceDir")
        val zipFile = archiveFile.get().asFile
        zipFile.delete()
        // `-y` stores symlinks as symlinks instead of following them (the default `zip` behavior,
        // and Gradle's `Zip` task, both dereference).
        execOperations.exec {
            workingDir = sourceDir
            commandLine("zip", "-y", "-r", zipFile.absolutePath, xcframeworkDir.name)
        }.result.get()
    }
}
