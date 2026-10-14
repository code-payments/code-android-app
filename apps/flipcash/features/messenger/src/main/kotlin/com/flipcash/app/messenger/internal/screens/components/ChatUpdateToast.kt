package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.app.updates.UpdateToastTiming
import com.flipcash.app.updates.showUpdateToast
import com.flipcash.features.messenger.R
import com.getcode.ui.components.toast.LocalFloatingToastHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one-time "New version available" toast in a chat (node 11705:162): 1s after the chat opens,
 * with the keyboard down, above the composer. It covers the newest messages, so it times out after
 * 6s, and the keyboard opening takes it down. Shares the once-per-version limit with the tab homes.
 *
 * [enabled] is whether the chat has a composer to sit above.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatUpdateToast(enabled: Boolean) {
    val updater = LocalAppUpdater.current
    val toasts = LocalFloatingToastHost.current ?: return
    val message = stringResource(R.string.title_newVersionAvailable)
    val update = stringResource(R.string.action_update)
    val imeVisible = WindowInsets.isImeVisible
    val keyboardUp by rememberUpdatedState(imeVisible)
    // Not the effect's own scope: marking the version seen clears the pending toast, and the toast
    // has to outlive that. Leaving the chat cancels this scope, and with it the toast.
    val scope = rememberCoroutineScope()
    var showing by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        delay(UpdateToastTiming.SETTLE_MILLIS)
        val toast = updater.updateToast.value ?: return@LaunchedEffect
        if (keyboardUp) return@LaunchedEffect
        showing = scope.launch {
            withTimeoutOrNull(UpdateToastTiming.CHAT_TIMEOUT_MILLIS) {
                updater.showUpdateToast(
                    toasts = toasts,
                    update = toast,
                    message = message,
                    actionLabel = update,
                    duration = SnackbarDuration.Indefinite,
                )
            }
        }
    }

    LaunchedEffect(imeVisible) {
        if (imeVisible) showing?.cancel()
    }
}
