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
