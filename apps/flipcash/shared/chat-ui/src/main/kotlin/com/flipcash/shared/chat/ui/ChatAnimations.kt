package com.flipcash.shared.chat.ui

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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color

// All chat animation spring specs in one place.
object ChatAnimations {
    // Message bubble insertion — scale from [insertionScale] about the bottom corner on the sender's
    // side, plus opacity and [insertionRise].
    // Matches iOS insertion: .spring(duration: 0.27, bounce: 0.15).
    val insertion: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 542f)
    const val insertionScale = 0.9f
    // How far below its slot a newly appended row starts, as a share of its own height. It rides
    // up into place rather than fading in over the row above. iOS insertionRise.
    const val insertionRise = 0.35f
    // The transcript riding up to make room for a new row. The same stiffness as [insertion] so the
    // rows and the new message move together, but without the bounce: the list can't scroll past
    // its newest message, so an overshoot would be clipped and the swing back would leave it short.
    val insertionPush: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 542f)

    // Rows resizing in place (a receipt moving on, a reaction): glides to the new layout and never
    // bounces. Matches iOS reflow: .spring(duration: 0.35, bounce: 0).
    private const val ReflowStiffness = 322f
    private val reflowIntSize: SpringSpec<IntSize> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = ReflowStiffness)

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

    // Receipt label exit when a new message is sent — the row closes the gap on [reflow] while the
    // line fades out fast enough to be gone before the bubble below reaches it. iOS
    // receiptExitFade: 0.08 s.
    val receiptExit: ExitTransition = shrinkVertically(reflowIntSize) + fadeOut(tween(durationMillis = 80))

    // Reaction pills, from iOS ChatMotion's reaction springs and scales.
    // A pill arriving — iOS reaction: .spring(duration: 0.32, bounce: 0.35), from 40%.
    val reactionEnter: SpringSpec<Float> = spring(dampingRatio = 0.65f, stiffness = 386f)
    const val reactionEnterScale = 0.4f
    // A pill leaving — iOS reactionExit: .spring(duration: 0.2, bounce: 0), to 60%. Quicker than
    // its arrival and without overshoot, so it doesn't read as the pill coming back.
    val reactionExit: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 987f)
    const val reactionExitScale = 0.6f
    // Pills sliding to make room and the row resizing — iOS reactionReflow, which is reflow.
    val reactionReflowOffset: SpringSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = ReflowStiffness)
    val reactionReflowHeight: SpringSpec<Int> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = ReflowStiffness)
    // A count or selected state changing in place — iOS reactionChange: .spring(duration: 0.24, bounce: 0).
    val reactionChange: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 685f)
    val reactionChangeColor: SpringSpec<Color> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 685f)
    val reactionChangeDp: SpringSpec<Dp> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 685f)
    val reactionChangeOffset: SpringSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 685f)
    val reactionChangeSize: SpringSpec<IntSize> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 685f)
}
