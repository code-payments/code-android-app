plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.flipcashNamespace}.test.utils"
}

dependencies {
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.junit)
    implementation(project(":libs:coroutines"))
}
