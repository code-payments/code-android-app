plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.util.datetime"
}

dependencies {
    api(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(project(":libs:test-utils"))
}
