package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize

// All chat animation spring specs in one place.
internal object ChatAnimations {
    // Message bubble insertion — scale from 0.95 + opacity.
    // Matches iOS insertionSpring: .spring(duration: 0.23, bounce: 0.27).
    val insertion: SpringSpec<Float> = spring(dampingRatio = 0.73f, stiffness = 746f)

    // Typing indicator entry/exit — scale from 0.95 + opacity.
    val typingIndicator: SpringSpec<Float> = spring(dampingRatio = 0.73f, stiffness = Spring.StiffnessHigh)

    // Action bar <-> composer swap — scale from 0.95 + opacity.
    val composerSwap: SpringSpec<Float> = spring(dampingRatio = 0.69f, stiffness = Spring.StiffnessHigh)

    // Delivered label appearance — scale from 0.95 + opacity.
    // Matches iOS deliveredSpring: .spring(duration: 0.4, bounce: 0.12).
    val delivered: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 250f)

    // Delivered -> Read label swap — scale + opacity.
    val readSwap: SpringSpec<Float> = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessHigh)

    // Long-press lift — the row dips under the finger, then springs up while it stands selected.
    // Matches the scale UIKit's context menu gives its preview on iOS.
    val lift: SpringSpec<Float> = spring(dampingRatio = 0.68f, stiffness = 600f)

    // Reply mode entry/exit — the bar grows a strip on top of itself and shrinks back.
    // Matches iOS replySurface: .spring(duration: 0.22, bounce: 0).
    //
    // No bounce, and that is the point: the transcript's bottom inset tracks the bar's height every
    // frame, so an overshoot here drags every message past where it settles and back.
    val replySurface: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 816f)
    private val replySurfaceIntSize: SpringSpec<IntSize> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 816f)

    // Asymmetric, as on iOS: nothing fades in, because the clip edge uncovering the quote is the
    // whole effect, and a fade on top of it reads as a second animation. Going away it does fade,
    // so the quote dissolves rather than being sliced off by an edge moving over text that is still
    // fully opaque.
    val replySurfaceEnter: EnterTransition =
        expandVertically(replySurfaceIntSize, expandFrom = Alignment.Top)
    val replySurfaceExit: ExitTransition =
        shrinkVertically(replySurfaceIntSize, shrinkTowards = Alignment.Top) + fadeOut(replySurface)

    // The flash a jump leaves on the message it landed on: white at full, held long enough to be
    // caught by an eye still following the scroll, then faded off.
    //
    // A tween rather than a spring, unlike everything above it: this one runs from full to nothing
    // with no competing target to be interrupted by, so there is no settling for a spring to
    // express — only a rate, and a linear one is what reads as a light going out.
    const val attentionHoldMs = 250L
    val attentionFade: TweenSpec<Float> = tween(durationMillis = 750, easing = LinearEasing)

    // Receipt label exit when a new message is sent — fade out + collapse.
    private val deliveredIntSize: SpringSpec<IntSize> = spring(dampingRatio = 0.88f, stiffness = 250f)
    val receiptExit: ExitTransition = shrinkVertically(deliveredIntSize) + fadeOut(delivered)
}
