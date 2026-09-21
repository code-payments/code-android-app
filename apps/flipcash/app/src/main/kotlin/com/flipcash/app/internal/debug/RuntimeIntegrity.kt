package com.flipcash.app.internal.debug

import android.app.ActivityOptions
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build

/**
 * Whether the runtime actually provides the framework methods for the API level it reports.
 *
 * Some runtimes — x86_64 VMs wearing a copied device fingerprint, cloud phones, repackaging
 * sandboxes — report an `SDK_INT` their `framework.jar` does not back. Library code gated on
 * `SDK_INT >= 34` then takes the branch and throws [NoSuchMethodError] from a call site we do not
 * own, so there is nothing to fix in the app. Three such crashes reached Bugsnag from one such
 * device: CameraX calling `Context.getDeviceId()` (`6a8f47a7b5ee91bed8ac6cac`, survived via
 * `launchBestEffort`), Play Billing calling
 * `ActivityOptions.setPendingIntentBackgroundActivityStartMode(int)`
 * (`6aac41d158142669ea746929`), and WorkManager calling `JobScheduler.forNamespace(String)`
 * (`6aac426c58142669ea748612`).
 *
 * Tagging the event lets triage filter these out without discarding them, so we keep visibility
 * into how often it happens.
 */
internal object RuntimeIntegrity {

    /** Probed once per process; reflection on a handful of methods is cheap but not free. */
    val reportsApiLevelHonestly: Boolean by lazy {
        declaresMethodsForApiLevel(Build.VERSION.SDK_INT, ::declaresMethod)
    }
}

/**
 * A framework method that should exist at [sinceSdk], addressed reflectively so this file compiles
 * and runs below that API level.
 */
internal data class FrameworkMethod(
    val owner: Class<*>,
    val name: String,
    val parameterTypes: List<Class<*>>,
    val sinceSdk: Int,
)

/**
 * The API-34 methods our dependencies call behind an `SDK_INT >= 34` gate. Each one has already
 * thrown [NoSuchMethodError] in production on a runtime claiming API 34.
 *
 * The declaring classes themselves predate API 34, so naming them here is safe; only the methods
 * are new.
 */
internal val ApiLevelProbes: List<FrameworkMethod> = listOf(
    FrameworkMethod(
        owner = Context::class.java,
        name = "getDeviceId",
        parameterTypes = emptyList(),
        sinceSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    ),
    FrameworkMethod(
        owner = ActivityOptions::class.java,
        name = "setPendingIntentBackgroundActivityStartMode",
        parameterTypes = listOf(Int::class.javaPrimitiveType!!),
        sinceSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    ),
    FrameworkMethod(
        owner = JobScheduler::class.java,
        name = "forNamespace",
        parameterTypes = listOf(String::class.java),
        sinceSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    ),
)

/**
 * `true` when every probe that [sdkInt] promises is actually present.
 *
 * Probes above [sdkInt] are skipped: a device honestly running API 33 is expected not to have
 * them. [declaresMethod] is injected so this stays a pure function under unit test, where
 * `Build.VERSION.SDK_INT` reads 0.
 */
internal fun declaresMethodsForApiLevel(
    sdkInt: Int,
    declaresMethod: (FrameworkMethod) -> Boolean,
    probes: List<FrameworkMethod> = ApiLevelProbes,
): Boolean = probes.filter { it.sinceSdk <= sdkInt }.all(declaresMethod)

/**
 * Reflective existence check. Catches [Throwable] rather than [NoSuchMethodException] because a
 * runtime this broken can fail the lookup itself with a [LinkageError].
 */
internal fun declaresMethod(method: FrameworkMethod): Boolean = try {
    method.owner.getMethod(method.name, *method.parameterTypes.toTypedArray())
    true
} catch (_: Throwable) {
    false
}
