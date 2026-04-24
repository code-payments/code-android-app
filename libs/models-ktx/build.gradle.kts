plugins {
    alias(libs.plugins.flipcash.android.library)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.models"
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:crypto:kin"))
    implementation(project(":libs:currency"))
    api(project(":libs:models"))
    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    implementation(libs.sodium.bindings)

    implementation(project(":definitions:code:models"))
}
