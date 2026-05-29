plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.push"
}

dependencies {
    implementation(libs.javax.inject)
}
