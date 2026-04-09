plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.persistence.sources"
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)

    implementation(project(":apps:flipcash:shared:persistence:db"))

    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:utils"))

    implementation(project(":services:flipcash"))
}
