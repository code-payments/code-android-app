import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.gradle.kotlin.dsl.withType

/**
 * Opt-in Apple targets for `flipcash.kmp.app.library` modules whose common code no longer needs
 * the JDK.
 *
 * Declares the same Apple targets as `flipcash.kmp.library`, so a `flipcash.kmp.library` module
 * (such as `:libs:encryption:ed25519`) can depend on these modules in common code. `macosArm64Test`
 * runs the common tests natively without a simulator.
 *
 * Usage, next to `flipcash.kmp.app.library`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.app.library)
 *     alias(libs.plugins.flipcash.kmp.apple)
 * }
 * ```
 */
class KmpAppleConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    iosArm64()
                    iosSimulatorArm64()
                    iosX64()
                    macosArm64()
                    macosX64()
                }
            }
            // As in the JS convention: a module whose commonTest holds only helpers (e.g.
            // :libs:parcelable's probe) has nothing to run natively.
            tasks.withType<KotlinNativeTest>().configureEach {
                failOnNoDiscoveredTests.set(false)
            }
            // `-Pflipcash.ios.simulator=<udid>` runs the simulator tests on that already-booted
            // simulator (one claimed through loupe), instead of KGP's own standalone default device.
            providers.gradleProperty("flipcash.ios.simulator").orNull?.let { udid ->
                tasks.withType<KotlinNativeSimulatorTest>().configureEach {
                    device.set(udid)
                    standalone.set(false)
                }
            }
        }
    }
}
