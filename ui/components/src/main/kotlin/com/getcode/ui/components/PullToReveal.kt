package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Hoisted state for [PullToReveal].
 *
 * The reveal slot is hidden by default and only shown by an explicit pull-down
 * (over-scroll) gesture once the pull passes the threshold — analogous to
 * `PullToRefreshState`, except the revealed content stays in place until [hide]
 * is called rather than auto-collapsing on release.
 */
@Stable
class PullToRevealState internal constructor(initiallyRevealed: Boolean) {

    /** Whether the reveal slot is currently shown. */
    var isRevealed: Boolean by mutableStateOf(initiallyRevealed)
        internal set

    /** Downward distance (px) accumulated toward the reveal threshold while hidden. */
    var distancePulled: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * Whether a pull-down gesture is currently being captured. A capture begins on a
     * downward over-pull while hidden and lasts until the gesture ends — so the
     * component keeps owning that drag even after the reveal threshold is crossed.
     */
    var isCapturing: Boolean by mutableStateOf(false)
        internal set

    /** Show the reveal slot and reset the accumulated pull. */
    fun reveal() {
        distancePulled = 0f
        isRevealed = true
    }

    /** Hide the reveal slot and reset any in-progress pull / capture. */
    fun hide() {
        distancePulled = 0f
        isCapturing = false
        isRevealed = false
    }

    companion object {
        val Saver: Saver<PullToRevealState, Boolean> = Saver(
            save = { it.isRevealed },
            restore = { PullToRevealState(initiallyRevealed = it) },
        )
    }
}

/** Remembers a [PullToRevealState] that survives configuration changes. */
@Composable
fun rememberPullToRevealState(initiallyRevealed: Boolean = false): PullToRevealState =
    rememberSaveable(saver = PullToRevealState.Saver) {
        PullToRevealState(initiallyRevealed = initiallyRevealed)
    }

object PullToRevealDefaults {
    /** Default pull distance required before the reveal slot is shown. */
    val Threshold: Dp = 56.dp

    /** Default transition used to animate the reveal slot in (expand + fade). */
    val Enter: EnterTransition = fadeIn() + expandVertically()

    /** Default transition used to animate the reveal slot out (shrink + fade). */
    val Exit: ExitTransition = fadeOut() + shrinkVertically()
}

/**
 * Vertically stacks [revealContent] above [content].
 *
 * [revealContent] is hidden by default and animates in (expand + fade) when the
 * user pulls [content] down past [threshold] while it is already scrolled to the
 * top. The reveal is driven by nested-scroll over-pull, so [content] must contain
 * a scrollable that dispatches nested scroll (e.g. a `LazyColumn`).
 *
 * Unlike pull-to-refresh, the revealed slot remains visible until
 * [PullToRevealState.hide] is called — the caller decides when to collapse it.
 *
 * @param onSuppressDismissChange invoked when the component starts/stops "owning" a
 * downward gesture. While `true`, an ancestor's competing drag (e.g. a bottom
 * sheet's drag-to-dismiss) should be disabled so the pull reveals instead of
 * dismissing; the value stays `true` for the whole reveal gesture — not just while
 * hidden — so a single hard pull cannot reveal and then dismiss in one motion.
 * @param enter transition applied when [revealContent] animates in. Override (e.g.
 * with a spring-backed `expandVertically`) to tune the reveal motion.
 * @param exit transition applied when [revealContent] animates out.
 */
@Composable
fun PullToReveal(
    revealContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRevealState = rememberPullToRevealState(),
    threshold: Dp = PullToRevealDefaults.Threshold,
    enabled: Boolean = true,
    onSuppressDismissChange: ((Boolean) -> Unit)? = null,
    enter: EnterTransition = PullToRevealDefaults.Enter,
    exit: ExitTransition = PullToRevealDefaults.Exit,
    content: @Composable () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val connection = remember(state, thresholdPx, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Any upward scroll cancels an in-progress pull / capture.
                if (enabled && available.y < 0f) {
                    state.distancePulled = 0f
                    state.isCapturing = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Leftover downward delta means the child is at the top and is being
                // over-pulled. Anything else (upward, or consumed by the list) is left
                // alone so normal scrolling is untouched.
                if (!enabled || available.y <= 0f) return Offset.Zero

                // Begin capturing only on a user drag while hidden; once we do, we own
                // the rest of the gesture (through the reveal threshold) so an ancestor
                // can't also act on it mid-drag.
                if (!state.isCapturing && !state.isRevealed &&
                    source == NestedScrollSource.UserInput
                ) {
                    state.isCapturing = true
                }
                if (!state.isCapturing) return Offset.Zero

                // Accumulate toward the threshold from the user's drag only.
                if (source == NestedScrollSource.UserInput) {
                    state.distancePulled += available.y
                    if (state.distancePulled >= thresholdPx) state.reveal()
                }
                // Consume the over-pull (drag and its trailing fling) so it never
                // reaches an ancestor — the sheet stays perfectly still while revealing.
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Gesture ended — release ownership so a fresh pull can reach the
                // ancestor again (e.g. to dismiss the sheet now that search is shown).
                state.distancePulled = 0f
                state.isCapturing = false
                return Velocity.Zero
            }
        }
    }

    val suppressDismiss = enabled && (!state.isRevealed || state.isCapturing)
    LaunchedEffect(suppressDismiss, onSuppressDismissChange) {
        onSuppressDismissChange?.invoke(suppressDismiss)
    }

    Column(modifier = modifier.nestedScroll(connection)) {
        AnimatedVisibility(visible = state.isRevealed, enter = enter, exit = exit) {
            revealContent()
        }
        content()
    }
}
