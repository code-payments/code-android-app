import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for KMP library modules.
 *
 * Applies:
 *   - `org.jetbrains.kotlin.multiplatform`
 *   - `com.android.kotlin.multiplatform.library`
 *
 * After applying this plugin the `kotlin {}` DSL block is available for configuring targets,
 * source sets, and dependencies.
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.library)
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
        }
    }
}
