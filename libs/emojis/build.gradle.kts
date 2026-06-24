plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.emojis"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore)
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
