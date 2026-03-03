plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.qr"
}

dependencies {
    implementation(project(":libs:code-detection"))

    implementation(libs.zxing)
    implementation(libs.play.service.ml.barcode)
    implementation(libs.javax.inject)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.hilt.android)

    implementation(libs.bugsnag)
}
