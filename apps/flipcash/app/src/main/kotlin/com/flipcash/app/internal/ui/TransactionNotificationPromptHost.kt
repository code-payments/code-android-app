package com.flipcash.app.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.flipcash.app.tokens.TransactionNotificationPrompt
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberNotificationPermission
import kotlinx.coroutines.flow.filter

/**
 * Asks, once ever, whether the user wants notifications for transactions that complete while they
 * are away. Hosted at the app level because the system permission dialog needs an activity-bound
 * launcher that outlives the flow that requested it.
 */
@Composable
internal fun TransactionNotificationPromptHost(prompt: TransactionNotificationPrompt) {
    val permission = rememberNotificationPermission()
    val currentPermission by rememberUpdatedState(permission)
    val title = stringResource(R.string.title_getNotified)
    val message = stringResource(R.string.description_getNotified)
    val notNow = stringResource(R.string.action_notNow)
    val turnOn = stringResource(R.string.action_turnOnNotifications)

    LaunchedEffect(prompt) {
        prompt.pending.filter { it }.collect {
            prompt.consume()
            // Auto-granted OS versions report Granted, so this never shows there.
            if (prompt.hasBeenShown() || currentPermission.status != PermissionResult.NotRequested) {
                return@collect
            }
            prompt.markShown()
            BottomBarManager.showMessage(
                title = title,
                message = message,
                actions = listOf(
                    BottomBarAction(text = turnOn) { currentPermission.launch() },
                    BottomBarAction(
                        text = notNow,
                        style = BottomBarManager.BottomBarButtonStyle.Text,
                    ),
                ),
            )
        }
    }
}
