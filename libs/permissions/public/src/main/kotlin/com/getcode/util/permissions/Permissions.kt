package com.getcode.util.permissions

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val PREFS_NAME = "permissions"

/**
 * Restores the last known [PermissionResult] for [permission] from SharedPreferences.
 *
 * Returns [PermissionResult.NotRequested] if no result has been persisted,
 * meaning the permission has never been requested in any session.
 */
/**
 * Restores the last known denial state for [permission] from SharedPreferences.
 *
 * [PermissionResult.Granted] is never persisted — the OS is the sole source of truth
 * for granted state. This avoids stale "Granted" entries when the user selects
 * "Only this time" or revokes the permission via system Settings.
 */
private fun SharedPreferences.restoreDenialState(permission: String): PermissionResult =
    when (getString(permission, null)) {
        "Denied" -> PermissionResult.Denied
        "PermanentlyDenied" -> PermissionResult.PermanentlyDenied
        else -> PermissionResult.NotRequested
    }

/**
 * Persists denial state for [permission] to SharedPreferences.
 *
 * Only [PermissionResult.Denied] and [PermissionResult.PermanentlyDenied] are stored.
 * [PermissionResult.Granted] clears any prior denial record.
 * [PermissionResult.NotRequested] is a no-op.
 */
private fun SharedPreferences.Editor.persistResult(permission: String, result: PermissionResult) {
    when (result) {
        PermissionResult.Denied -> putString(permission, "Denied")
        PermissionResult.PermanentlyDenied -> putString(permission, "PermanentlyDenied")
        PermissionResult.Granted -> remove(permission)
        PermissionResult.NotRequested -> return
    }
}

/**
 * Creates and remembers a [PermissionHandle] for the given [config].
 *
 * Handles the full permission lifecycle:
 * - Restores last known state from SharedPreferences on composition (survives process death)
 * - Shows the system dialog via [PermissionHandle.launch]
 * - Persists the launcher result and delivers it via [onResult]
 * - Re-evaluates state on [Lifecycle.Event.ON_RESUME] to catch changes made in system Settings
 *
 * ### OEM compatibility
 * [androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale] is only used inside the launcher
 * result callback — the one place it's reliably post-user-action. It is never used for
 * cross-session state inference, which is unreliable on many OEMs (e.g. Samsung).
 *
 * ### Stale lambda safety
 * [onResult] is wrapped in [rememberUpdatedState] so the launcher always invokes the
 * latest lambda, even if the composition scope (navigator, analytics, etc.) has changed
 * since the dialog was shown.
 *
 * @param config Describes the permission and its UI error metadata.
 * @param onResult Called with the [PermissionResult] after the user interacts with the
 *   system dialog, or immediately if the permission is already granted / below [PermissionConfig.minSdk].
 * @return A [PermissionHandle] whose [PermissionHandle.status] is observable Compose state.
 *
 * @see rememberNotificationPermission
 * @see rememberCameraPermission
 */
@Composable
fun rememberPermission(
    config: PermissionConfig,
    onResult: (PermissionResult) -> Unit = {},
): PermissionHandle {
    val checker = LocalPermissionChecker.current
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val initialStatus = remember(config.permission) {
        when {
            checker.isGranted(config.permission) -> PermissionResult.Granted
            !config.requiresRuntimeRequest -> PermissionResult.Granted
            else -> prefs.restoreDenialState(config.permission)
        }
    }

    val handle = remember(config.permission) { PermissionHandle(initialStatus) }

    // Suppresses ON_RESUME re-evaluation while the system dialog is in flight.
    // Without this, the activity resumes when the dialog dismisses and re-evaluates
    // state before the launcher result fires, producing a race condition.
    var launchingDialog by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (launchingDialog) {
                    // Dialog is dismissing — launcher result will update status
                    launchingDialog = false
                } else {
                    // Returning from system Settings — re-evaluate from source of truth
                    handle.status = when {
                        checker.isGranted(config.permission) -> PermissionResult.Granted
                        else -> prefs.restoreDenialState(config.permission)
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val result = when {
            isGranted -> PermissionResult.Granted
            // shouldShowRationale is only reliable here — immediately after user action
            checker.shouldShowRationale(config.permission) -> PermissionResult.Denied
            else -> PermissionResult.PermanentlyDenied
        }
        prefs.edit { persistResult(config.permission, result) }
        handle.status = result
        currentOnResult(result)
    }

    handle.launcher = {
        when {
            !config.requiresRuntimeRequest -> {
                handle.status = PermissionResult.Granted
                currentOnResult(PermissionResult.Granted)
            }
            checker.isGranted(config.permission) -> {
                handle.status = PermissionResult.Granted
                currentOnResult(PermissionResult.Granted)
            }
            else -> {
                launchingDialog = true
                systemLauncher.launch(config.permission)
            }
        }
    }

    return handle
}

/**
 * Convenience wrapper for [android.Manifest.permission.POST_NOTIFICATIONS].
 * Auto-granted below API 33 (TIRAMISU).
 *
 * @see rememberPermission
 * @see PermissionConfigs.notifications
 */
@Composable
fun rememberNotificationPermission(
    onResult: (PermissionResult) -> Unit = {},
) = rememberPermission(PermissionConfigs.notifications(), onResult)

/**
 * Convenience wrapper for [android.Manifest.permission.CAMERA].
 *
 * @see rememberPermission
 * @see PermissionConfigs.camera
 */
@Composable
fun rememberCameraPermission(
    onResult: (PermissionResult) -> Unit = {},
) = rememberPermission(PermissionConfigs.camera(), onResult)

/**
 * Convenience wrapper for [android.Manifest.permission.WRITE_EXTERNAL_STORAGE].
 * Auto-granted on API 29+ (Q) — use MediaStore or SAF for storage access there.
 *
 * @see rememberPermission
 * @see PermissionConfigs.externalStorage
 */
@Composable
fun rememberStoragePermission(
    onResult: (PermissionResult) -> Unit = {},
) = rememberPermission(PermissionConfigs.externalStorage(), onResult)