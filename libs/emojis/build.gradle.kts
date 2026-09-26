plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.emojis"
}

// reactions.json is read from the repo's canonical test-vectors/ rather than copied in.
androidComponents {
    onVariants { variant ->
        val vectors = rootProject.file("test-vectors").path
        variant.hostTests.values.forEach { it.sources.resources?.addStaticSourceDirectory(vectors) }
        variant.deviceTests.values.forEach { it.sources.assets?.addStaticSourceDirectory(vectors) }
    }
}

dependencies {
    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Define the task to fetch and generate emoji data
afterEvaluate {
    tasks.matching { it.name.matches(Regex("compile.*Kotlin")) }.configureEach {
        dependsOn("generateEmojiList")
    }
}

tasks.register<GenerateEmojiList>("generateEmojiList") {
    emojiUrl.set("https://unicode.org/Public/emoji/16.0/emoji-test.txt")
    emojiKeywordsUrl.set("https://raw.githubusercontent.com/unicode-org/cldr-json/refs/heads/main/cldr-json/cldr-annotations-full/annotations/en/annotations.json")
    emojiCacheDir.set(layout.projectDirectory)
    outputDir.set(layout.projectDirectory.dir("src/main/kotlin/com/getcode/libs/emojis/generated"))
}
