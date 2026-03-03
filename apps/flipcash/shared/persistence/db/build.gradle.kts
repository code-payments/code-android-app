plugins {
    alias(libs.plugins.flipcash.android.feature)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.persistence.db"

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bugsnag)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)

    implementation(project(":libs:models"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:utils"))

    implementation(project(":services:flipcash"))
}
