plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.logging"

    buildTypes {
        getByName("release") {
            buildConfigField("Boolean", "NOTIFY_ERRORS", "true")
        }
        getByName("debug") {
            buildConfigField(
                "Boolean",
                "NOTIFY_ERRORS",
                tryReadProperty(rootProject.rootDir, "NOTIFY_ERRORS", "false")
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(project(":libs:test-utils"))

    api(libs.timber)
    implementation(libs.androidx.annotation)
    implementation(libs.grpc.kotlin)
    implementation(project(":libs:messaging"))
}
