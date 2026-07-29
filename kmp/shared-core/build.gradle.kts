/**
 * KMP beachhead (milestone M1), packaged for iOS via TouchLab KMMBridge.
 *
 * Goal: prove the Kotlin -> iOS SPM pipeline WITHOUT moving or editing any existing code.
 * This module has no Android target — the Android app keeps consuming the original library modules
 * (e.g. `:libs:network:jwt`) exactly as before. Here we only compile the already-native-safe sources
 * *in place* (via `srcDir`, see below) and let KMMBridge assemble an XCFramework and generate the
 * SPM `Package.swift` (at the repo root) that iOS consumes.
 *
 * Local dev loop (no publishing):  ./gradlew :kmp:shared-core:spmDevBuild
 *   -> builds a debug XCFramework and rewrites the root Package.swift to a local path.
 * Publish (CI, needs GitHub creds): ./gradlew :kmp:shared-core:kmmBridgePublish
 *   -> uploads the XCFramework to a GitHub Release and pins url+checksum in Package.swift.
 */
plugins {
    // Applied versionless: the Kotlin Gradle plugin is already on the build classpath (via
    // build-logic), so we bind to that version rather than requesting one (which conflicts).
    kotlin("multiplatform")
    // GitHub artifact manager plugin; it also applies the base `co.touchlab.kmmbridge` plugin
    // (which registers the `kmmbridge { }` extension), so we must NOT apply the base plugin too.
    id("co.touchlab.kmmbridge.github") version "1.2.1"
    `maven-publish`
}

kotlin {
    // iOS targets (the deliverable). `macosArm64` is included so the framework can be linked and
    // exercised by a host-side `swift test`. Add `androidTarget()` later, once we're ready to have
    // the Android app consume this module instead of the original libraries (a separate step).
    listOf(
        iosArm64(),           // physical devices
        iosSimulatorArm64(),  // Apple Silicon simulator
        iosX64(),             // Intel simulator
        macosArm64(),         // host link-test only (Apple Silicon)
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SharedCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            // Compile the EXISTING sources in place. Nothing is moved: the files stay owned by
            // their original module, which keeps building as an Android library untouched.
            // Only add directories whose every file is Kotlin/Native-safe.
            kotlin.srcDir(rootDir.resolve("libs/network/jwt/src/main/kotlin"))
        }
    }
}

kmmbridge {
    // Production consumption path: publish the XCFramework to GitHub Releases of this repo and pin
    // url+checksum in the generated Package.swift. Reads GITHUB_REPO / GITHUB_PUBLISH_TOKEN at
    // publish time only — the local `spmDevBuild` flow does not upload, so no creds are needed to dev.
    gitHubReleaseArtifacts()
    spm(swiftToolVersion = "5.9") {
        iOS { v("15") }
        macOS { v("13") } // host link-testing slice
    }
}
