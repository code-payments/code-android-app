package com.getcode.util.permissions

/**
 * Represents the possible states of a runtime permission.
 *
 * These states are persisted across sessions via [android.content.SharedPreferences] to avoid
 * relying on [androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale], which is
 * unreliable across OEMs and process boundaries.
 */
sealed interface PermissionResult {
    /** The permission has been granted by the user or is implicitly granted below [PermissionConfig.minSdk]. */
    object Granted : PermissionResult

    /** The permission has never been requested in any session. */
    object NotRequested : PermissionResult

    /** The permission was denied once. The system dialog can still be shown again. */
    object Denied : PermissionResult

    /** The user selected "Don't ask again", or the device does not support re-requesting. */
    object PermanentlyDenied : PermissionResult
}