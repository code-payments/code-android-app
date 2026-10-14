package com.flipcash.app.updates

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import com.getcode.ui.components.toast.FloatingToastHostState

/**
 * Shows the one-time "New version available" toast for [update] and records it as seen before it
 * appears, so a tap, a swipe and a timeout all count. Update starts Play's flexible download.
 * Shows nothing while [toasts] is covered, and then leaves the version unseen.
 */
suspend fun AppUpdateController.showUpdateToast(
    toasts: FloatingToastHostState,
    update: OptionalUpdate,
    message: String,
    actionLabel: String,
    duration: SnackbarDuration = SnackbarDuration.Long,
) {
    if (toasts.isCovered) return
    markUpdateToastShown(update)
    if (toasts.show(message = message, actionLabel = actionLabel, duration = duration) == SnackbarResult.ActionPerformed) {
        startOptionalUpdate()
    }
}
