import com.getcode.buildlogic.testfixtures.GenerateTestFixtures
import com.getcode.buildlogic.testfixtures.TestFixturesExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compiles a KMP module's `src/commonTest/resources` into a generated `TestFixtures.kt` on
 * `commonTest`, so the same fixtures are readable from every target (Kotlin/Native test binaries
 * ship no resource bundle, so a resource-based loader only ever runs on the JVM).
 *
 * Usage in a module's `build.gradle.kts`:
 * ```
 * plugins {
 *     alias(libs.plugins.flipcash.kmp.test.fixtures)
 * }
 *
 * testFixtures {
 *     packageName = "com.getcode.vendor"
 * }
 * ```
 *
 * The generated directory is registered on `commonTest` and the AGP lint tasks are made to depend
 * on the generator -- adding the source directory only carries the dependency to the Kotlin compile
 * tasks, while lint reads the same directories straight off disk and Gradle then fails the build
 * over an undeclared dependency on generated sources.
 */
class KmpTestFixturesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = extensions.create<TestFixturesExtension>("testFixtures")
            extension.fixtures.convention(layout.projectDirectory.dir("src/commonTest/resources"))

            val generateTestFixtures = tasks.register<GenerateTestFixtures>("generateTestFixtures") {
                packageName.set(extension.packageName)
                fixtures.set(extension.fixtures)
                outputDirectory.set(layout.buildDirectory.dir("generated/testFixtures"))
            }

            tasks.matching { it.name.startsWith("lint") || it.name.endsWith("LintModel") }
                .configureEach { dependsOn(generateTestFixtures) }

            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> {
                    sourceSets.named("commonTest") { kotlin.srcDir(generateTestFixtures) }
                }
            }
        }
    }
}
