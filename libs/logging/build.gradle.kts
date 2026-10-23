plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.logging"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.annotation)
            implementation(libs.grpc.kotlin)
            implementation(project(":libs:messaging"))
            implementation(libs.ocp.client.protocol)
            implementation(libs.flipcash2.client.protocol)
        }
        androidMain.dependencies {
            api(libs.timber)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
        }
    }
}
