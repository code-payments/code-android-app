import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
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
 *   - `jvm` -- the desktop client
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
            val javaVersion = libs.findVersion("android-java").get().requiredVersion

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    compileSdk {
                        version = release(compileSdkVersion) {
                            minorApiLevel = compileSdkMinorVersion
                        }
                    }
                    minSdk = minSdkVersion
                    // The single-variant KMP target picks one build type of each Android-library
                    // dependency, and AGP defaults that to release. Prefer debug: the host tests run
                    // under `flipcashTestDebug` next to the Android modules' debug unit tests, and
                    // selecting release made that task build a second, release copy of every Android
                    // library a KMP module reaches (25 modules, compileReleaseKotlin included). The
                    // app still resolves the release variants for its own release build.
                    localDependencySelection {
                        selectBuildTypeFrom.set(listOf("debug", "release"))
                    }
                }

                iosArm64()
                iosSimulatorArm64()
                iosX64()
                macosArm64()
                macosX64()

                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                    }
                }

                // KGP compiles the Java its KSP processors emit (Dagger factories) against
                // jvmMainCompileClasspath, which does not ask for a platform type. With debug and
                // release both selectable above, a library that publishes android and jvm variants
                // (libsodium bindings) is ambiguous there. Ask for jvm.
                project.configurations.matching { it.name == "jvmMainCompileClasspath" }.configureEach {
                    attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                }
            }
        }
    }
}
