package com.flipcash.app.core.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 *
 * With [immediatelyIfImeVisible], a screen that arrives while the keyboard is already up asks for
 * focus at once, without waiting for the slide. Focus moves straight from the step being left to
 * this field, so the keyboard changes type instead of dropping and rising again. The keyboard is
 * read as the screen enters; if it is down then, focus waits for the slide as usual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestFocusWhenSettled(
    focusRequester: FocusRequester,
    onlyOnce: Boolean = false,
    immediatelyIfImeVisible: Boolean = false,
) {
    RequestFocusOnArrival(
        focusRequester = focusRequester,
        onlyOnce = onlyOnce,
        immediatelyIfImeVisible = immediatelyIfImeVisible,
        imeVisible = { WindowInsets.isImeVisible },
    )
}

/** [RequestFocusWhenSettled] with the keyboard's visibility passed in, for tests. */
@Composable
internal fun RequestFocusOnArrival(
    focusRequester: FocusRequester,
    onlyOnce: Boolean,
    immediatelyIfImeVisible: Boolean,
    imeVisible: @Composable () -> Boolean,
) {
    val own = LocalNavAnimatedContentScope.current.transition
    val host = LocalHostEnterTransition.current
    val windowInfo = LocalWindowInfo.current
    var requested by rememberSaveable { mutableStateOf(false) }
    // Only screens that opt in read the insets, so the others don't recompose with the keyboard.
    val imeUp = immediatelyIfImeVisible && imeVisible()
    // As the screen entered. The keyboard opening later, for this very field, changes nothing.
    val imeVisibleOnEntry = remember { imeUp }
    LaunchedEffect(own, host) {
        if (onlyOnce && requested) return@LaunchedEffect
        snapshotFlow {
            val arrived = imeVisibleOnEntry || (own.isSettled && (host?.isSettled ?: true))
            arrived && windowInfo.isWindowFocused
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
