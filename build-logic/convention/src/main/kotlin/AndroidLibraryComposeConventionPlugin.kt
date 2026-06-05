import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("flipcash.android.library")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFile.set(
                    rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
                )
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                "implementation"(platform(libs.findLibrary("compose-bom").get()))
                "implementation"(libs.findLibrary("compose-ui").get())
                "implementation"(libs.findLibrary("compose-foundation").get())
            }
        }
    }
}
