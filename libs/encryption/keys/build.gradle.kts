plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.encryption.keys"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:encryption:base58"))
            implementation(project(":libs:encryption:sha256"))
            implementation(project(":libs:encryption:utils"))
            api(project(":libs:parcelable"))

            implementation(libs.protobuf.kotlin.lite)
            implementation(libs.grpc.okhttp)
            implementation(libs.grpc.kotlin)
            implementation(libs.bundles.kotlinx.serialization)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
        }
    }
}
