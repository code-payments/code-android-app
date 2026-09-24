import com.getcode.buildlogic.analytics.CheckAnalyticsEventsPage
import com.getcode.buildlogic.analytics.GenerateAnalyticsEvents
import com.getcode.buildlogic.analytics.WriteAnalyticsEventsPage
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
 *
 * `EVENTS.md`, the catalogue as a readable page, is checked in next to `events.toml`.
 * `writeAnalyticsEventsPage` rewrites it; `checkAnalyticsEventsPage` fails when it is stale.
 */
class AnalyticsCatalogueConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateAnalyticsEvents = tasks.register<GenerateAnalyticsEvents>("generateAnalyticsEvents") {
                catalogue.set(layout.projectDirectory.file("events.toml"))
                outputDirectory.set(layout.buildDirectory.dir("generated/analytics/commonMain/kotlin"))
            }

            val catalogueFile = layout.projectDirectory.file("events.toml")
            val pageFile = layout.projectDirectory.file("EVENTS.md")
            tasks.register<WriteAnalyticsEventsPage>("writeAnalyticsEventsPage") {
                catalogue.set(catalogueFile)
                page.set(pageFile)
            }
            tasks.register<CheckAnalyticsEventsPage>("checkAnalyticsEventsPage") {
                catalogue.set(catalogueFile)
                page.set(pageFile)
                fixCommand.set("./gradlew ${path.substringBeforeLast(':')}:writeAnalyticsEventsPage")
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
