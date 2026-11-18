package com.flipcash.app.core.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * The enter transition of the screen that hosts a nested flow. A flow's own steps animate inside
 * their own nav display, so the slide that brought the whole flow in is not visible to them
 * through [LocalNavAnimatedContentScope]; the host provides it here.
 */
val LocalHostEnterTransition: ProvidableCompositionLocal<Transition<EnterExitState>?> =
    compositionLocalOf { null }

private val Transition<EnterExitState>.isSettled: Boolean
    get() = currentState == EnterExitState.Visible && targetState == EnterExitState.Visible

/**
 * Asks [focusRequester] for focus once the screen has finished arriving, rather than on first
 * composition. Focusing during a push raises the keyboard mid-slide, which resizes the screen under
 * the transition and makes the field jump. It also waits for the window to have focus, which a
 * screen opened on a cold start from a link does not have yet.
 *
 * With [onlyOnce], focus is asked for on the screen's first arrival only, so coming back to it with
 * Back leaves the keyboard closed.
 */
@Composable
fun RequestFocusWhenSettled(focusRequester: FocusRequester, onlyOnce: Boolean = false) {
    val own = LocalNavAnimatedContentScope.current.transition
    val host = LocalHostEnterTransition.current
    val windowInfo = LocalWindowInfo.current
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(own, host) {
        if (onlyOnce && requested) return@LaunchedEffect
        snapshotFlow {
            own.isSettled && (host?.isSettled ?: true) && windowInfo.isWindowFocused
        }.first { it }
        requested = true
        try {
            focusRequester.requestFocus()
        } catch (ex: IllegalStateException) {
            // Seen in staging with OnWindowFocusedRequester: "FocusRequester is not initialized."
            Timber.e(ex, "Failed to set focus to text field")
        }
    }
}
