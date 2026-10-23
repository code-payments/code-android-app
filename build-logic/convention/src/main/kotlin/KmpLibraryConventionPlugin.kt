import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for KMP library modules.
 *
 * Applies:
 *   - `org.jetbrains.kotlin.multiplatform`
 *   - `com.android.kotlin.multiplatform.library`
 *
 * and declares the targets every shared library builds for:
 *   - `android` -- compileSdk and minSdk from the version catalog
 *   - `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64` -- the Kotlin/Native
 *     slices of the `SharedCore` framework iOS consumes
 *
 * A module still sets its own `namespace`, and opts into host/device tests, inside `kotlin { android {} }`.
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.library)
 * }
 *
 * kotlin {
 *     android {
 *         namespace = "com.example"
 *         withHostTest {}
 *     }
 * }
 * ```
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val compileSdkVersion = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            val compileSdkMinorVersion = libs.findVersion("android-compileSdkMinor").get().requiredVersion.toInt()
            val minSdkVersion = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    compileSdk {
                        version = release(compileSdkVersion) {
                            minorApiLevel = compileSdkMinorVersion
                        }
                    }
                    minSdk = minSdkVersion
                }

                iosArm64()
                iosSimulatorArm64()
                iosX64()
                macosArm64()
                macosX64()
            }
        }
    }
}
