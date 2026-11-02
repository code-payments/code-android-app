package com.flipcash.shared.chat.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.alerts.ReactionAlertContent.Kind
import com.flipcash.shared.chat.alerts.ReactionAlertController
import com.flipcash.shared.chat.alerts.ReactionAlertState
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln

/**
 * Draws the controller's alert over the app, as iOS does: a glass card that fades and unblurs in
 * just under the status bar with a small spring settle, and lifts back out the same way. A new
 * reaction on the card already up swaps its text in place with a small bump. Swipe up dismisses,
 * a tap opens the message. The haptic plays once the card has landed, not when it starts.
 *
 * With animations off (Reduce Motion) the card only fades.
 */
@Composable
fun ReactionAlertHost(
    controller: ReactionAlertController,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val live by controller.state.collectAsState()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val animate = remember(live) { ValueAnimator.areAnimatorsEnabled() }

    // Kept after [live] clears so the exit has content to draw.
    var shown by remember { mutableStateOf<ReactionAlertState?>(null) }
    if (live != null) shown = live

    val lift = remember { Animatable(EnterLiftDp) }
    val scale = remember { Animatable(EnterScale) }
    val blur = remember { Animatable(EnterBlurDp) }
    val alpha = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var landed by remember { mutableStateOf(false) }

    LaunchedEffect(live != null) {
        if (live != null) {
            if (alpha.value < 0.01f) {
                lift.snapTo(if (animate) EnterLiftDp else 0f)
                scale.snapTo(if (animate) EnterScale else 1f)
                blur.snapTo(if (animate) EnterBlurDp else 0f)
            }
            coroutineScope {
                launch { alpha.animateTo(1f, tween(if (animate) 180 else 200, easing = LinearOutSlowInEasing)) }
                if (animate) {
                    launch { blur.animateTo(0f, tween(180, easing = LinearOutSlowInEasing)) }
                    launch { lift.animateTo(0f, EnterSpring) }
                    launch { scale.animateTo(1f, EnterSpring) }
                }
            }
        } else if (shown != null) {
            landed = false
            coroutineScope {
                launch { alpha.animateTo(0f, tween(if (animate) 220 else 200, easing = FastOutLinearInEasing)) }
                if (animate) {
                    launch { lift.animateTo(ExitLiftDp, tween(220, easing = FastOutLinearInEasing)) }
                    launch { scale.animateTo(ExitScale, tween(220, easing = FastOutLinearInEasing)) }
                    launch { blur.animateTo(ExitBlurDp, tween(220, easing = FastOutLinearInEasing)) }
                }
            }
            shown = null
            drag.snapTo(0f)
        }
    }

    // Haptics wait for the card to land; a merge into the card already up bumps it and ticks.
    LaunchedEffect(live?.generation) {
        val state = live ?: return@LaunchedEffect
        val isUpdate = landed
        if (!isUpdate) {
            delay(LAND_MILLIS)
        } else if (animate) {
            scope.launch {
                scale.animateTo(1.025f, spring(dampingRatio = 0.6f, stiffness = springStiffness(0.18f)))
                delay(110)
                scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = springStiffness(0.32f)))
            }
        }
        view.performHapticFeedback(hapticFor(state.content.kind, isUpdate))
        landed = true
    }

    val current = shown ?: return
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gesture = Modifier
            .pointerInput(controller) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag.value < -SWIPE_DISMISS_DP) {
                            controller.dismiss()
                        } else {
                            scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = springStiffness(0.3f))) }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { _, delta ->
                    scope.launch { drag.snapTo(drag.value + delta / density.density) }
                }
            }
            .clickable(interactionSource = interaction, indication = null) { controller.onTap() }

        ReactionAlertPill(
            content = current.content,
            maxWidth = maxWidth - 24.dp,
            hazeState = hazeState,
            animate = animate,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 4.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer {
                    translationY = (lift.value + dragResistance(drag.value)) * density.density
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    if (Build.VERSION.SDK_INT >= 31 && blur.value > 0.1f) {
                        val radius = blur.value * density.density
                        renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                    }
                }
                .then(gesture),
        )
    }
}

/** Follows an upward drag one to one and resists a downward one, so the card feels held in place. */
private fun dragResistance(dp: Float): Float = if (dp < 0f) dp else 10f * ln(1f + dp / 10f)

/** SwiftUI's `spring(response:)` as a Compose stiffness: (2π / response)². */
private fun springStiffness(response: Float): Float {
    val omega = (2.0 * Math.PI / response).toFloat()
    return omega * omega
}

/** Boost = success; a first reaction = a firm tap; a merge into the card already up = a light tick. */
private fun hapticFor(kind: Kind, isUpdate: Boolean): Int = when {
    kind == Kind.Boost ->
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
    isUpdate -> HapticFeedbackConstants.CLOCK_TICK
    else -> HapticFeedbackConstants.CONTEXT_CLICK
}

private const val EnterLiftDp = -28f
private const val ExitLiftDp = -24f
private const val EnterScale = 0.92f
private const val ExitScale = 0.94f
private const val EnterBlurDp = 6f
private const val ExitBlurDp = 4f
private const val SWIPE_DISMISS_DP = 20f

/** The spring settle: iOS `spring(response: 0.42, dampingFraction: 0.72)`. */
private val EnterSpring = spring<Float>(dampingRatio = 0.72f, stiffness = springStiffness(0.42f))

/** iOS plays the haptic 240 ms into the drop, as the spring lands. */
private const val LAND_MILLIS = 240L
