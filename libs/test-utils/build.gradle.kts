plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.flipcashNamespace}.test.utils"
}

dependencies {
    api(libs.eddsa)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.junit)
    implementation(project(":libs:coroutines"))
}
