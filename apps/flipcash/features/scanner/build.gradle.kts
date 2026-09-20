plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.scanner"
}

dependencies {
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:bill-customization"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:router"))
    implementation(project(":apps:flipcash:shared:session"))

    implementation(project(":libs:code-detection"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":libs:quickresponse"))
    implementation(project(":libs:vibrator:bindings"))
    implementation(project(":ui:biometrics"))
    implementation(project(":ui:scanner"))
    // Declared rather than inherited through `:ui:scanner`'s `api`: this module names
    // `KikCodeScannerImpl`, `StaticImageAnalyzerImpl` and `KikCodeAnalyzer` directly.
    implementation(project(":vendor:kik:scanner"))
    implementation(libs.androidx.camerax.view)
    implementation(libs.androidx.foundation.layout)
}
