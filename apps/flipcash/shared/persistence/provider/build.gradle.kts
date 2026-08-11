plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.persistence.provider"
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
}

dependencies {
    implementation(project(":apps:flipcash:shared:persistence:db"))
    // Needed to reference FlipcashDatabase's RoomDatabase supertype at the call site
    // for the post-migration profile backfill.
    implementation(libs.bundles.room)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
}
