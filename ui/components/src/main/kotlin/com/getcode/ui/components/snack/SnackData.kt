package com.getcode.ui.components.snack

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult

data class SnackData(
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

/**
 * Shows [data], replacing any snackbar already on screen rather than queueing behind it.
 */
suspend fun SnackbarHostState.showSnackbar(data: SnackData): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        message = data.message,
        actionLabel = data.actionLabel,
        duration = data.duration
    )
}