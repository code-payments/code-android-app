import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for app-layer KMP modules that build for Android and the desktop JVM only.
 *
 * Unlike `flipcash.kmp.library` it declares no Kotlin/Native targets: these modules sit on Android
 * and JVM libraries (gRPC, Room, DataStore, OkHttp) with no native counterpart. Because every target
 * is a JVM, `commonMain` sees the JDK, so code that only needs `java.*` moves there unchanged and
 * only `android.*` uses need an `expect`/`actual` or an interface.
 *
 * Applies:
 *   - `org.jetbrains.kotlin.multiplatform`
 *   - `com.android.kotlin.multiplatform.library`
 *   - `org.jetbrains.kotlin.plugin.serialization`
 *   - `org.jetbrains.kotlin.plugin.parcelize` -- generates Android parcel code for classes marked
 *     with `com.getcode.libs.parcelable.Parcelize`, the common stand-in in `:libs:parcelable`
 *
 * and declares:
 *   - `android` -- compileSdk and minSdk from the version catalog, host tests enabled with the same
 *     options `flipcash.android.library` sets on unit tests
 *   - `jvm` -- the desktop client
 *
 * The AGP KMP library target is single-variant: it has no `buildTypes` and no `BuildConfig`, so a
 * value that differed between debug and release must come from the app at runtime.
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.app.library)
 * }
 *
 * kotlin {
 *     android {
 *         namespace = "com.example"
 *     }
 * }
 * ```
 */
class KmpAppLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
                apply("org.jetbrains.kotlin.plugin.parcelize")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val compileSdkVersion = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            val compileSdkMinorVersion = libs.findVersion("android-compileSdkMinor").get().requiredVersion.toInt()
            val minSdkVersion = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
            val javaVersion = libs.findVersion("android-java").get().requiredVersion

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(javaVersion))
                }

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    compileSdk {
                        version = release(compileSdkVersion) {
                            minorApiLevel = compileSdkMinorVersion
                        }
                    }
                    minSdk = minSdkVersion
                    // Hilt's KSP processors emit Java (factories, hilt_aggregated_deps), which the
                    // KMP Android target only compiles when Java compilation is enabled.
                    withJava()
                    withHostTestBuilder {}.configure {
                        isReturnDefaultValues = true
                        isIncludeAndroidResources = true
                    }
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                    }
                }

                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                    }
                }

                compilerOptions {
                    optIn.addAll(
                        "kotlin.time.ExperimentalTime",
                        "kotlin.ExperimentalUnsignedTypes",
                        "kotlin.RequiresOptIn",
                        "kotlin.ExperimentalMultiplatform"
                    )
                    freeCompilerArgs.addAll(
                        "-Xexpect-actual-classes",
                        "-P",
                        "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=com.getcode.libs.parcelable.Parcelize"
                    )
                }

                sourceSets.named("commonMain") {
                    dependencies {
                        implementation(libs.findLibrary("kotlinx-coroutines-core").get())
                    }
                }
                sourceSets.named("androidMain") {
                    dependencies {
                        implementation(libs.findLibrary("timber").get())
                    }
                }
                sourceSets.named("androidHostTest") {
                    dependencies {
                        implementation(libs.findLibrary("kotlin-test-junit").get())
                        // Provides robolectric.properties, which pins the Robolectric SDK level.
                        implementation(project(":libs:test-utils"))
                    }
                }
                sourceSets.named("jvmTest") {
                    dependencies {
                        implementation(libs.findLibrary("kotlin-test-junit").get())
                    }
                }
            }

            tasks.withType<Test> {
                failOnNoDiscoveredTests.set(false)
                maxHeapSize = "2g"
            }
        }
    }
}
