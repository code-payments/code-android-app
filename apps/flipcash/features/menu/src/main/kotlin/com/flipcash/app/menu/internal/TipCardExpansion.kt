package com.flipcash.app.menu.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import kotlin.math.sign

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

    private val overdragAnimatable = Animatable(0f)

    /** 0 at rest, 1 full screen; anywhere in between while dragging or springing. */
    val progress: Float get() = animatable.value

    /**
     * How far a downward drag has pushed the card past full screen, in the same units as
     * [progress] — resisted by [OverdragResistance], and deliberately not part of the progress
     * itself. Down is where expanding already put the card, so there is nothing there to scrub:
     * only the card's position gives, and its size and everything reading the progress hold still.
     */
    val overdrag: Float get() = overdragAnimatable.value

    /**
     * The part of the drag that has run off the end, before resistance. Kept unresisted so the
     * finger has to give all of it back before the card starts moving home again — the alternative
     * is a card that leaves the end as soon as the finger turns around, a quarter of the way behind
     * where it was pushed.
     */
    private var overflow = 0f

    /** The recogniser's slack, waiting for the first delta to hand it back to. See [startDrag]. */
    private var pendingSlack = 0f

    var isExpanded by mutableStateOf(false)
        private set

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    fun expand(velocity: Float = 0f, spec: AnimationSpec<Float> = ExpansionSpring) {
        isExpanded = true
        animate(target = 1f, velocity = velocity, spec = spec)
    }

    fun collapse(velocity: Float = 0f) {
        isExpanded = false
        animate(target = 0f, velocity = velocity, spec = ExpansionSpring)
    }

    /**
     * Opens a drag, with the [slack] the recogniser swallowed getting there — touch slop, in
     * progress units. It is handed back on the first delta so the card picks up from where it
     * stands rather than jumping that distance the moment the gesture is recognised. iOS hands back
     * its own 10pt activation distance for the same reason.
     */
    fun startDrag(slack: Float) {
        pendingSlack = slack
        overflow = 0f
    }

    /**
     * Moves the card by [deltaProgress] under the finger, cancelling whatever spring was running.
     *
     * Pulled up past its slot the card simply stops: it has arrived, and a finger that has run out
     * of card should feel the end. Pushed down past full screen it gives a little instead — see
     * [overdrag] — because there the card has nowhere to go but is still being asked to move.
     */
    fun dragBy(deltaProgress: Float) {
        val delta = deltaProgress + pendingSlack * sign(deltaProgress)
        if (deltaProgress != 0f) pendingSlack = 0f
        scope.launch {
            val moved = animatable.value + overflow + delta
            animatable.snapTo(moved.coerceIn(0f, 1f))
            overflow = (moved - 1f).coerceAtLeast(0f)
            overdragAnimatable.snapTo(overflow * OverdragResistance)
        }
    }

    /** Springs to whichever end [settlesExpanded] picks, carrying the release velocity into it. */
    fun settle(velocity: Float, flingThreshold: Float) {
        overflow = 0f
        pendingSlack = 0f

        val staysExpanded = settlesExpanded(animatable.value, velocity, flingThreshold)
        // A pull that fell short has a short way back, so it takes the short spring; one that
        // committed is running the whole transition and takes the transition's own.
        val spec = if (staysExpanded) ReturnSpring else ExpansionSpring

        scope.launch { overdragAnimatable.animateTo(0f, spec) }
        if (staysExpanded) expand(velocity, spec) else collapse(velocity)
    }

    private fun animate(target: Float, velocity: Float, spec: AnimationSpec<Float>) {
        scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = spec,
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
 * slower goes on whether the pull covered enough of the way home ([CollapseThreshold]).
 *
 * [velocity] and [flingThreshold] are both in progress-per-second, so the caller divides the
 * gesture's pixel velocity by the distance the card actually has left to travel — a flick means
 * the same thing on a tall display as on a short one.
 */
internal fun settlesExpanded(progress: Float, velocity: Float, flingThreshold: Float): Boolean =
    when {
        velocity <= -flingThreshold -> false
        velocity >= flingThreshold -> true
        else -> progress > 1f - CollapseThreshold
    }

/**
 * How much of the card's travel home a pull has to cover for the release to finish the job, as
 * iOS's `collapseThreshold` does.
 *
 * Well short of half, because the two ends are not equally likely: the card is only ever dragged
 * from one of them, by someone who has already decided to put it away. Asking for half of the
 * display's height before that counts made the card feel like it was resisting.
 */
private const val CollapseThreshold = 0.3f

/**
 * The fraction of a downward drag the card follows past full screen. Down is where expanding
 * already took the card, so the pull has nowhere to take it and gives only enough to show that the
 * drag is being felt. iOS's `overdragResistance`.
 */
private const val OverdragResistance = 0.25f

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
 * Puts a pull that fell short back where it started — iOS's `settle`, `.spring(response: 0.3,
 * dampingFraction: 0.85)`. Shorter than [ExpansionSpring] because the card has barely moved, and
 * spending the full transition on a few dp of travel reads as a stall rather than a return.
 */
private val ReturnSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 439f)

/**
 * The ceiling on the release velocity a settle carries into its spring, in progress-per-second —
 * which is really a ceiling on how far the card bounces past the end it landed on, since that is
 * what an underdamped spring does with speed it is handed.
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
 * iOS never needs the cap because it never carries the gesture's velocity into the spring at all:
 * its settle starts from a standstill, where this damping ratio overshoots half a percent.
 */
private const val MaxSettleVelocity = 4f
