package com.flipcash.app.core.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.first

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
 * the transition and makes the field jump.
 */
@Composable
fun RequestFocusWhenSettled(focusRequester: FocusRequester) {
    val own = LocalNavAnimatedContentScope.current.transition
    val host = LocalHostEnterTransition.current
    LaunchedEffect(own, host) {
        snapshotFlow { own.isSettled && (host?.isSettled ?: true) }.first { it }
        focusRequester.requestFocus()
    }
}
