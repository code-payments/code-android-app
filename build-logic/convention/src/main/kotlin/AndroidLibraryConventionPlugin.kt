import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.serialization")
                if (!providers.gradleProperty("skipCoverage").orNull.toBoolean()) {
                    apply("org.jetbrains.kotlinx.kover")
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 36

                defaultConfig {
                    minSdk = 29
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_21
                    targetCompatibility = JavaVersion.VERSION_21
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }

                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                    optIn.addAll(
                        "kotlin.time.ExperimentalTime",
                        "kotlin.ExperimentalUnsignedTypes",
                        "kotlin.RequiresOptIn"
                    )
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                "implementation"(libs.findLibrary("timber").get())
                "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())
            }
        }
    }
}
