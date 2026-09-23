import com.getcode.buildlogic.analytics.GenerateAnalyticsEvents
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Generates a KMP module's analytics builders and value enums from its `events.toml` into
 * `commonMain`, so both apps call the same Kotlin and the catalogue is the only place an event
 * is written down. See `com.getcode.buildlogic.analytics` for the format.
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.analytics.catalogue)
 * }
 * ```
 *
 * The generated directory is registered on `commonMain`, which carries the task dependency to
 * every compilation, including the Kotlin/Native ones the XCFramework build links. Lint reads
 * source directories straight off disk, so it is made to depend on the task explicitly, as in
 * [KmpTestFixturesConventionPlugin].
 */
class AnalyticsCatalogueConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateAnalyticsEvents = tasks.register<GenerateAnalyticsEvents>("generateAnalyticsEvents") {
                catalogue.set(layout.projectDirectory.file("events.toml"))
                outputDirectory.set(layout.buildDirectory.dir("generated/analytics/commonMain/kotlin"))
            }

            tasks.matching { it.name.startsWith("lint") || it.name.endsWith("LintModel") }
                .configureEach { dependsOn(generateAnalyticsEvents) }

            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    sourceSets.named("commonMain") { kotlin.srcDir(generateAnalyticsEvents) }
                }
            }
        }
    }
}
