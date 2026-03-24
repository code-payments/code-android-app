package com.getcode.util.permissions

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Encapsulates the current state and request trigger for a single runtime permission.
 *
 * Returned by [rememberPermission] and its convenience wrappers. Callers observe
 * [status] for reactive UI and call [launch] to request the permission.
 *
 * Instances are [Stable] — [status] is backed by [mutableStateOf] and will
 * trigger recomposition on change.
 *
 * The constructor is internal — always obtain via [rememberPermission].
 */
@Stable
class PermissionHandle internal constructor(
    initialStatus: PermissionResult,
) {
    /**
     * The current [PermissionResult]. Backed by Compose state — changes trigger recomposition.
     *
     * Updated by:
     * - [rememberPermission] on initial composition (restored from SharedPreferences)
     * - The system launcher result callback after the user interacts with the dialog
     * - [androidx.lifecycle.Lifecycle.Event.ON_RESUME] when returning from system Settings
     */
    var status by mutableStateOf(initialStatus)
        internal set

    /** @suppress Internal launcher wired by [rememberPermission]. */
    internal var launcher: () -> Unit = {}

    /** True if [status] is [PermissionResult.Granted]. */
    val isGranted get() = status == PermissionResult.Granted

    /** True if [status] is [PermissionResult.PermanentlyDenied]. */
    val isPermanentlyDenied get() = status == PermissionResult.PermanentlyDenied

    /**
     * Requests the permission.
     *
     * Behaviour varies by current state:
     * - Already granted → immediately calls `onResult(Granted)`, no dialog shown
     * - Below [PermissionConfig.minSdk] → immediately calls `onResult(Granted)`, no dialog shown
     * - Otherwise → shows the system permission dialog; result delivered via `onResult`
     */
    fun launch() = launcher()
}