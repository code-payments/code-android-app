plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.coinbase.onramp"
}

dependencies {
    implementation(libs.kotlinx.datetime)

    implementation(project(":libs:models"))

    api(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
