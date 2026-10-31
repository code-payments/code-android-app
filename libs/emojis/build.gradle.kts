import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.emojis"
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(libs.bundles.hilt.common)
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.datastore.preferences.core)
        }
        androidMain.dependencies {
            implementation(libs.hilt.android)
        }
        // reactions.json is read from the repo's canonical test-vectors/ rather than copied in.
        commonTest {
            resources.srcDir(rootProject.file("test-vectors"))
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.bundles.unit.testing)
        }
        getByName("androidDeviceTest") {
            resources.srcDir(rootProject.file("test-vectors"))
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("jvmMainImplementation", libs.dagger)
}

// Define the task to fetch and generate emoji data. KSP reads src/commonMain/kotlin too, so it
// has to wait for the generated sources as well as the Kotlin compile.
val generateEmojiList by tasks.registering(GenerateEmojiList::class) {
    emojiUrl.set("https://unicode.org/Public/emoji/16.0/emoji-test.txt")
    emojiKeywordsUrl.set("https://raw.githubusercontent.com/unicode-org/cldr-json/refs/heads/main/cldr-json/cldr-annotations-full/annotations/en/annotations.json")
    emojiCacheDir.set(layout.projectDirectory)
    outputDir.set(layout.projectDirectory.dir("src/commonMain/kotlin/com/getcode/libs/emojis/generated"))
}

tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(generateEmojiList) }
tasks.matching { it.name.startsWith("ksp") || it.name.startsWith("lint") || it.name.endsWith("SourcesJar") }
    .configureEach { dependsOn(generateEmojiList) }

// :apps:flipcash:core drags in firebase's protolite-well-known-types, which duplicates classes in
// protobuf-javalite; the instrumented test never touches either, so keep the clash off its APK.
configurations.matching { it.name.contains("DeviceTest", ignoreCase = true) }.configureEach {
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
}
