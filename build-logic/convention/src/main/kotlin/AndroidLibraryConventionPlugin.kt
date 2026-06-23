import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
                if (!providers.gradleProperty("skipCoverage").orNull.toBoolean()) {
                    apply("org.jetbrains.kotlinx.kover")
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val compileSdkVersion = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            val minSdkVersion = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
            val javaVersion = libs.findVersion("android-java").get().requiredVersion

            extensions.configure<LibraryExtension> {
                compileSdk = compileSdkVersion

                defaultConfig {
                    minSdk = minSdkVersion
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                    unitTests.isIncludeAndroidResources = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(javaVersion)
                    targetCompatibility = JavaVersion.toVersion(javaVersion)
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(javaVersion))
                }

                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                    optIn.addAll(
                        "kotlin.time.ExperimentalTime",
                        "kotlin.ExperimentalUnsignedTypes",
                        "kotlin.RequiresOptIn"
                    )
                }
            }

            tasks.withType<Test> {
                failOnNoDiscoveredTests.set(false)
                maxHeapSize = "2g"
            }

            dependencies {
                "implementation"(libs.findLibrary("timber").get())
                "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())
                // AGP's built-in Kotlin no longer auto-selects the kotlin-test
                // JUnit variant, so provide it explicitly for all library modules.
                "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
                // Provides robolectric.properties pinning SDK to 36 (latest Robolectric supports).
                if (path != ":libs:test-utils") {
                    "testImplementation"(project(":libs:test-utils"))
                }
            }
        }
    }
}
