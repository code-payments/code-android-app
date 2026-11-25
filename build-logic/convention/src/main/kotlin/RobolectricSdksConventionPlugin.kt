import com.getcode.buildlogic.robolectric.useGradleResolvedRobolectricSdks
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies [useGradleResolvedRobolectricSdks] on its own, for modules that run Robolectric tests but
 * apply none of the flipcash.* conventions that already call it (the app module).
 */
class RobolectricSdksConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.useGradleResolvedRobolectricSdks()
}
