import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidEd25519ShadowConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("test") {
                    java.srcDir(rootProject.file("testing/ed25519-shadow"))
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                "testImplementation"(libs.findLibrary("eddsa").get())
            }
        }
    }
}
