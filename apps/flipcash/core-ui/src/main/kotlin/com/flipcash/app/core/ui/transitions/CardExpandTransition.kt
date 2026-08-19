@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.flipcash.app.core.ui.transitions

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.geometry.Rect

/**
 * Timings + specs for the wallet card-expand transition (fanned deck ⇄ currency-info hero), porting
 * iOS #587's single `.smooth(0.32)` scalar.
 *
 * The wallet does **not** slide horizontally for this push. It holds and fades **in place**
 * ([openExit]) while the tapped card flies to the hero via the shared-bounds overlay and the deck
 * reorganises around the vacated slot. Because the flying card is hosted in the transition overlay it
 * is unaffected by the screen fade, so it stays fully opaque throughout — matching iOS's tapped card
 * (opacity 1). The destination fades in slightly delayed ([openEnter]) so the deck reorg is visible
 * first, mirroring iOS's `pageFollowDelay`.
 */
object CardExpandTransition {
    const val OpenMillis = 340

    // Kept ≥ the fly-back spring so the wallet's enter transition (which gates in-layer hosting via
    // currentState == PreEnter) stays active for the whole flight — otherwise the card would flip back
    // into the overlay mid-landing and snap on top of its neighbours.
    const val CloseMillis = 360

    /** Long enough to outlast the card's fly-back spring, keeping the detail source composed. */
    private const val FlightMillis = 440

    val openEnter: EnterTransition =
        fadeIn(tween(durationMillis = 160, delayMillis = OpenMillis - 160))
    val openExit: ExitTransition = fadeOut(tween(OpenMillis))

    // Close: the wallet appears immediately (no fade) so the returning card — rendered in-layer at its
    // natural deck z-order — is fully visible as it flies home and slides under its neighbours (in-layer
    // rendering avoids the overlay's land-on-top-then-snap). The detail screen is held composed but
    // invisible (alpha snaps to 0 via the flat easing, yet the composable lingers) so it still anchors
    // the shared element's source for the full flight.
    val closeEnter: EnterTransition = EnterTransition.None
    val closeExit: ExitTransition = fadeOut(tween(FlightMillis, easing = Easing { 1f }))

    // Interactive (drag) close: NavDisplay SEEKS this to the drag progress, so the specs must map
    // fraction → visible state (not snap). The wallet is revealed underneath (enter None) while the
    // detail fades out on top; the deck reorg + shared card seek in step underneath. Used only by the
    // predictive-pop path so the button close keeps its snap-invisible hand-off above.
    // Enter has a real (seekable) spec — NOT None — so the wallet's enter transition actually seeks with
    // the drag, driving the deck-reorg reassembly and the shared card's fly-back in step (None would snap
    // the wallet to rest and you'd only get a crossfade).
    val predictiveCloseEnter: EnterTransition = fadeIn(tween(FlightMillis))
    val predictiveCloseExit: ExitTransition = fadeOut(tween(FlightMillis))

    /** Critically-damped spring (≈ iOS `.smooth`) driving the card's fly between deck slot and hero. */
    val boundsTransform: BoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = Rect.VisibilityThreshold)
    }
}
