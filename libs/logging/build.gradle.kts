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

    api(libs.timber)
    implementation(libs.bugsnag)
    implementation(libs.rxjava)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.grpc.kotlin)
    implementation(project(":libs:messaging"))
}
