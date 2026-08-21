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

// Where `Package.swift` is written. CI points this at a checkout of
// `code-payments/flipcash-shared-core-spm`; locally it lands under the root
// build directory so `spmDevBuild` has somewhere to write without dirtying the
// repo. Left unset, KMMBridge would write it to this repo's root.
val spmPackageDir = findProperty("spmRepoDir") as String?
    ?: rootProject.layout.buildDirectory.dir("spm").get().asFile.path

kotlin {
    android {
        namespace = "com.flipcash.shared"
        compileSdk = 37
        minSdk = 29
    }
    
    val appleTargets = listOf(iosArm64(), iosSimulatorArm64(), iosX64())
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
    spm(spmDirectory = spmPackageDir, swiftToolVersion = "5.9") {
        iOS { v("15") }
    }
}
