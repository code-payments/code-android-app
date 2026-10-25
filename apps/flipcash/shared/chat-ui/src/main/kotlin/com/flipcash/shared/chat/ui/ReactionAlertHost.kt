package com.flipcash.shared.chat.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.alerts.ReactionAlertController
import com.flipcash.shared.chat.alerts.ReactionAlertState
import kotlinx.coroutines.delay

/**
 * Draws the controller's pill over the app. It walks the motion table: width grows out of the
 * cutout (180 ms), height and radius open on a spring, and on close the text fades, the height
 * collapses (260 ms in all) and the width returns to the cutout (200 ms).
 *
 * With animations off (Reduce Motion) the pill appears in its expanded shape and crossfades.
 */
@Composable
fun ReactionAlertHost(
    controller: ReactionAlertController,
    modifier: Modifier = Modifier,
) {
    val live by controller.state.collectAsState()
    val view = LocalView.current
    val density = LocalDensity.current
    val animate = remember(live) { ValueAnimator.areAnimatorsEnabled() }

    // Kept after [live] clears so the close animation has content to draw.
    var shown by remember { mutableStateOf<ReactionAlertState?>(null) }
    var phase by remember { mutableStateOf(ReactionAlertPhase.Rest) }
    if (live != null) shown = live

    LaunchedEffect(live != null) {
        if (live != null) {
            if (animate) {
                phase = ReactionAlertPhase.Compact
                delay(ReactionAlertPillSpec.GROW_MILLIS.toLong())
            }
            phase = ReactionAlertPhase.Expanded
        } else if (shown != null) {
            if (animate) {
                phase = ReactionAlertPhase.Compact
                delay(260)
            }
            phase = ReactionAlertPhase.Rest
            if (animate) delay(200)
            shown = null
        }
    }

    // One tap on the haptic per content swap, boost or reaction alike.
    LaunchedEffect(live?.generation) {
        if (live == null) return@LaunchedEffect
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }

    val current = shown ?: return
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.roundToPx() }
        val anchor = remember(screenWidthPx, view.rootWindowInsets) {
            val rects = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
            reactionAlertAnchor(rects, screenWidthPx)
        }
        val gesture = Modifier
            .pointerInput(controller) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -SWIPE_UP_PX) controller.dismiss() },
                ) { _, delta -> total += delta }
            }
            .clickable(interactionSource = interaction, indication = null) { controller.onTap() }

        val maxPillWidth = maxWidth - 16.dp
        when (anchor) {
            ReactionAlertAnchor.Banner -> AnimatedVisibility(
                visible = phase != ReactionAlertPhase.Rest,
                enter = slideInVertically(tween(300)) { -it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
            ) {
                ReactionAlertPill(
                    content = current.content,
                    phase = ReactionAlertPhase.Expanded,
                    inActiveChat = current.inActiveChat,
                    maxWidth = maxPillWidth,
                    animate = animate,
                    modifier = gesture,
                )
            }

            is ReactionAlertAnchor.Centered -> {
                val (w, h) = with(density) { anchor.rect.width().toDp() to anchor.rect.height().toDp() }
                ReactionAlertPill(
                    content = current.content,
                    phase = phase,
                    inActiveChat = current.inActiveChat,
                    restWidth = w,
                    restHeight = h,
                    maxWidth = maxPillWidth,
                    animate = animate,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = with(density) { anchor.rect.top.toDp() })
                        .then(if (phase == ReactionAlertPhase.Rest) Modifier else gesture),
                )
            }

            is ReactionAlertAnchor.Corner -> {
                val (w, h) = with(density) { anchor.rect.width().toDp() to anchor.rect.height().toDp() }
                val edge = with(density) {
                    if (anchor.atStart) anchor.rect.left.toDp() else (screenWidthPx - anchor.rect.right).toDp()
                }
                ReactionAlertPill(
                    content = current.content,
                    phase = phase,
                    inActiveChat = current.inActiveChat,
                    restWidth = w,
                    restHeight = h,
                    // The hole sits where the top row would, so content starts below it.
                    expandedTopInset = (h - 12.dp).coerceAtLeast(0.dp),
                    maxWidth = maxPillWidth - edge,
                    animate = animate,
                    modifier = Modifier
                        .align(if (anchor.atStart) Alignment.TopStart else Alignment.TopEnd)
                        .padding(
                            top = with(density) { anchor.rect.top.toDp() },
                            start = if (anchor.atStart) edge else 0.dp,
                            end = if (anchor.atStart) 0.dp else edge,
                        )
                        .then(if (phase == ReactionAlertPhase.Rest) Modifier else gesture),
                )
            }
        }
    }
}

private const val SWIPE_UP_PX = 40f
