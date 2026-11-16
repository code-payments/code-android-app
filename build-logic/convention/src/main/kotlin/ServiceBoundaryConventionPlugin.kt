import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Keeps the platform-neutral `:services:*` modules (the ones an SDK can ship) free of app code.
 *
 * Registers `checkServiceDependencies`, wired into `check`. It fails if any main runtime or
 * compile graph of the module resolves to a banned project or library: `:apps:*` or `:ui:*`, a DI
 * framework (Hilt/Dagger, Metro), Room, analytics, or Firebase.
 *
 * `settings.gradle.kts` applies this to every `:services:*` project except the app-facing ones,
 * so a new service module is checked without opting in.
 */
class ServiceBoundaryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val check = tasks.register("checkServiceDependencies", CheckServiceDependenciesTask::class.java) {
            group = "verification"
            description = "Fails if this service module depends on apps/, ui/, DI frameworks, Room, analytics or Firebase."
        }
        configurations.matching { it.isCheckedGraph() }.all {
            val name = name
            val root = incoming.resolutionResult.rootComponent
            check.configure { graphs.put(name, root) }
        }
        pluginManager.withPlugin("base") { tasks.named("check") { dependsOn(check) } }
        pluginManager.withPlugin("lifecycle-base") { tasks.named("check") { dependsOn(check) } }
    }

    private fun org.gradle.api.artifacts.Configuration.isCheckedGraph(): Boolean =
        isCanBeResolved &&
            !name.contains("test", ignoreCase = true) &&
            (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath") || name.endsWith("CompileKlibraries"))
}

@UntrackedTask(because = "Reads resolved dependency graphs and produces no output")
abstract class CheckServiceDependenciesTask : DefaultTask() {

    /** Resolved graph root per configuration name. */
    @get:Input
    abstract val graphs: MapProperty<String, ResolvedComponentResult>

    @TaskAction
    fun check() {
        val violations = sortedSetOf<String>()
        graphs.get().forEach { (configuration, root) ->
            walk(root) { from, to ->
                // Report where the graph first enters banned code, not every edge inside it.
                if (banReason(from) == null) {
                    banReason(to)?.let { violations += "$configuration: ${from.label()} -> ${to.label()} ($it)" }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Service module dependency rules broken (only libs/, other services, the transport and " +
                    "the -wire artifacts are allowed):\n" + violations.joinToString("\n") { "  $it" },
            )
        }
    }

    private fun walk(root: ResolvedComponentResult, visit: (ResolvedComponentResult, ResolvedComponentResult) -> Unit) {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
                visit(component, it.selected)
                queue += it.selected
            }
        }
    }

    private fun banReason(component: ResolvedComponentResult): String? =
        when (val id = component.id) {
            is ProjectComponentIdentifier -> when {
                id.projectPath.startsWith(":apps:") -> "app module"
                id.projectPath.startsWith(":ui:") -> "UI module (Android resources)"
                id.projectPath.contains("analytics") -> "analytics"
                else -> null
            }
            is ModuleComponentIdentifier -> BANNED_GROUPS.entries.firstOrNull { (prefix, _) ->
                id.group == prefix || id.group.startsWith("$prefix.")
            }?.value
            else -> null
        }

    private fun ResolvedComponentResult.label(): String = when (val id = id) {
        is ProjectComponentIdentifier -> id.projectPath
        else -> id.displayName
    }

    private companion object {
        val BANNED_GROUPS = mapOf(
            "com.google.dagger" to "Hilt/Dagger",
            "androidx.hilt" to "Hilt",
            "dev.zacsweers.metro" to "Metro",
            "androidx.room" to "Room",
            "com.google.firebase" to "Firebase",
            "com.mixpanel" to "analytics",
            "com.bugsnag" to "analytics",
        )
    }
}
