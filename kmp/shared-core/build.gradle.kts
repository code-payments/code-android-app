plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("co.touchlab.kmmbridge.github") version "1.2.1"
    `maven-publish`
}

group = "com.flipcash"
version = "0.1.0"

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
            export(project(":libs:encryption:base58"))
            export(project(":libs:encryption:sha256"))
            export(project(":libs:encryption:sha512"))
            export(project(":libs:encryption:hmac"))
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":libs:encryption:base58"))
                api(project(":libs:encryption:sha256"))
                api(project(":libs:encryption:sha512"))
                api(project(":libs:encryption:hmac"))
            }
        }
    }
}

kmmbridge {
    gitHubReleaseArtifacts()
    spm(swiftToolVersion = "5.9") {
        iOS { v("15") }
    }
}
