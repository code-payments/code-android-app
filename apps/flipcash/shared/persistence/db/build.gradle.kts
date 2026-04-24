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
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.bundles.room)
    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)

    implementation(project(":libs:models"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:utils"))

    implementation(project(":services:flipcash"))
}
