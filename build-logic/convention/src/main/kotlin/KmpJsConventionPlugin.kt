import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds a Kotlin/JS target for Node to a KMP module that already applies `flipcash.kmp.library` or
 * `flipcash.kmp.app.library`.
 *
 * Opt-in, like `flipcash.kmp.test.fixtures`: only modules whose `commonMain` is free of JVM APIs can
 * take it, and those are the ones the Node SDK needs. Code that needs Node APIs goes in `jsMain`.
 * `commonTest` runs on Node through `jsNodeTest`.
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.library)
 *     alias(libs.plugins.flipcash.kmp.js)
 * }
 * ```
 */
class KmpJsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    js {
                        nodejs {
                            testTask {
                                useMocha {
                                    // The vector suites walk every fixture in one test; mocha's 2s
                                    // default is too short for Long-limb crypto on Node.
                                    timeout = "60s"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
