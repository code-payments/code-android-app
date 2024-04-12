package com.getcode.ui.components

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult

data class SnackData(
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

suspend fun SnackbarHostState.showSnackbar(data: SnackData): SnackbarResult {
    return showSnackbar(
        message = data.message,
        actionLabel = data.actionLabel,
        duration = data.duration
    )
}