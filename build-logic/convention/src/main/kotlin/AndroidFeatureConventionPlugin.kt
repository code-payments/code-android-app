import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("flipcash.android.library.compose")
                apply("com.google.devtools.ksp")
                apply("dagger.hilt.android.plugin")
                apply("kotlin-parcelize")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    buildConfig = true
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                "implementation"(libs.findBundle("hilt").get())
                "ksp"(libs.findBundle("hilt-compiler").get())

                "implementation"(libs.findBundle("compose").get())
                "implementation"(libs.findLibrary("rinku-compose").get())

                // Common project dependencies
                "implementation"(project(":libs:coroutines"))
                "implementation"(project(":libs:logging"))
                "implementation"(project(":ui:core"))
                "implementation"(project(":ui:components"))
                "implementation"(project(":ui:navigation"))
                "implementation"(project(":ui:resources"))
                "implementation"(project(":ui:theme"))

                if (path != ":apps:flipcash:core") {
                    "implementation"(project(":apps:flipcash:core"))
                }
            }
        }
    }
}
