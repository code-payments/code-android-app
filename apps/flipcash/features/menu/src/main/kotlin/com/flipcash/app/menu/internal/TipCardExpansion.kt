package com.flipcash.app.menu.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "You" tab's full-screen state, as one continuous 0..1 progress rather than a boolean.
 *
 * Everything the expansion touches — the card's width, how far it has travelled out of its slot,
 * the page fading away beneath it, the Close row — is a reading of [progress], so a gesture can
 * park the whole page part-way through the transition and hand it back where it found it. The tap
 * affordances just drive the same progress to an end with a spring.
 *
 * [isExpanded] is the *intent*, not the position: it flips the moment a direction is committed to
 * (tap, or the release of a swipe) and stays put for the length of a drag, so the tab bar and the
 * list's scrolling don't flicker in and out as the finger crosses the half-way mark.
 */
@Stable
internal class TipCardExpansion(private val scope: CoroutineScope) {

    private val animatable = Animatable(0f)

    /** 0 at rest, 1 full screen; anywhere in between while dragging or springing. */
    val progress: Float get() = animatable.value

    var isExpanded by mutableStateOf(false)
        private set

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    fun expand(velocity: Float = 0f) {
        isExpanded = true
        animate(target = 1f, velocity = velocity)
    }

    fun collapse(velocity: Float = 0f) {
        isExpanded = false
        animate(target = 0f, velocity = velocity)
    }

    /**
     * Moves the card by [deltaProgress] under the finger, cancelling whatever spring was running.
     *
     * Clamped rather than rubber-banded, and unlike a settle it is allowed no overshoot at all: the
     * card is under the finger, and a finger that has run out of card should feel the end.
     */
    fun dragBy(deltaProgress: Float) {
        scope.launch { animatable.snapTo((animatable.value + deltaProgress).coerceIn(0f, 1f)) }
    }

    /** Springs to whichever end [settlesExpanded] picks, carrying the release velocity into it. */
    fun settle(velocity: Float, flingThreshold: Float) {
        if (settlesExpanded(animatable.value, velocity, flingThreshold)) {
            expand(velocity)
        } else {
            collapse(velocity)
        }
    }

    private fun animate(target: Float, velocity: Float) {
        scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = ExpansionSpring,
                initialVelocity = velocity.coerceIn(-MaxSettleVelocity, MaxSettleVelocity),
            )
        }
    }
}

@Composable
internal fun rememberTipCardExpansion(): TipCardExpansion {
    val scope = rememberCoroutineScope()
    return remember(scope) { TipCardExpansion(scope) }
}

/**
 * Where a released swipe lands: a flick wins on velocity alone, however far it got, and anything
 * slower than that falls to whichever end it is nearer.
 *
 * [velocity] and [flingThreshold] are both in progress-per-second, so the caller divides the
 * gesture's pixel velocity by the distance the card actually has left to travel — a flick means
 * the same thing on a tall display as on a short one.
 */
internal fun settlesExpanded(progress: Float, velocity: Float, flingThreshold: Float): Boolean =
    when {
        velocity <= -flingThreshold -> false
        velocity >= flingThreshold -> true
        else -> progress >= HalfWay
    }

private const val HalfWay = 0.5f

/**
 * How far a swipe must be moving at release to decide the outcome on its own, regardless of how far
 * it travelled. Material's own swipe threshold — low enough that a flick of the card is enough,
 * high enough that letting go of a slow drag doesn't count as one.
 */
internal val MinFlingVelocity = 125.dp

/**
 * The floor on the drag's travel distance, for the frames before the card's slot has been measured
 * (and for the pathological case of a display too short to move the card at all). Without it a drag
 * would divide by a travel of zero and snap the card shut on the first pixel.
 */
internal val MinDragTravel = 120.dp

/**
 * The whole expansion — card size, card position, the content sliding away, the Close row — runs on
 * one spring, as iOS does: `.spring(response: 0.45, dampingFraction: 0.85)`. SwiftUI's `response` is
 * the undamped period, so the equivalent Compose stiffness is `(2 * PI / 0.45) ^ 2`.
 */
private val ExpansionSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 195f)

/**
 * The ceiling on the release velocity a settle carries into [ExpansionSpring], in progress-per-
 * second — which is really a ceiling on how far the card bounces past the end it landed on, since
 * that is what an underdamped spring does with speed it is handed.
 *
 * Not clamped to no overshoot at all: the bounce is the card arriving somewhere and settling into
 * it, and a flick that stops dead reads as a dropped frame. What it can't be is the ~18% of the
 * travel an uncapped flick paid for — a card lifted clear out of its slot and up under the status
 * bar, taking the page with it. This cap costs at most 5.6% (measured, across every release point),
 * which is a bounce of about 10dp. [TipCardOvershootTest] holds both halves: some, and not much.
 *
 * It also floors how quickly a settle can be over. Uncapped, the hardest flick collapsed the card
 * in three frames, which is less a transition than a cut.
 *
 * iOS never needs the cap because it has no swipe — its spring always starts from a standstill,
 * where the same damping ratio overshoots half a percent.
 */
private const val MaxSettleVelocity = 4f
