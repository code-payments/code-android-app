plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.logging.grpc"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:logging"))
            implementation(libs.grpc.api)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
        }
    }
}
