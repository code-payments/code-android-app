plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.currency.math"
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)

    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
    implementation(libs.bugsnag)

    testImplementation(kotlin("test"))
}

val generateCurveTables by tasks.registering(GenerateCurveTables::class) {
    rustTableUrl.set("https://raw.githubusercontent.com/code-payments/flipcash-program/refs/heads/main/api/src/table.rs")
    outputDir.set(layout.projectDirectory.dir("src/main/assets"))

    // Always regenerate (useful for CI or development)
    // TODO: enable for CI when repo is public
    // forceRegenerate.set(true)
}

// Make sure tables are generated before assets are merged
tasks.named("preBuild") {
    dependsOn(generateCurveTables)
}
