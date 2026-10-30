package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The chat's backdrop: what sits [behind] a raised message, or the expanded request deck, is
 * dimmed and blurred. Applied to each surface that goes behind, rather than drawn as a layer over
 * them, so the thing raised in front stays sharp wherever it sits in the tree.
 *
 * [animationSpec] runs 0 (sharp) to 1 (behind); a caller pairing the backdrop with a transition of
 * its own passes that transition's spec so the two move together.
 */
@Composable
internal fun Modifier.chatBackdrop(
    behind: Boolean,
    animationSpec: AnimationSpec<Float> = spring(),
): Modifier {
    val fraction by animateFloatAsState(
        targetValue = if (behind) 1f else 0f,
        animationSpec = animationSpec,
        label = "chatBackdrop",
    )
    // Unbounded: the rectangle treatment would clip the blur at each surface's own edges and leave
    // a hard seam between neighbouring rows.
    return blur(BACKDROP_BLUR * fraction, BlurredEdgeTreatment.Unbounded)
        .graphicsLayer { alpha = 1f - (1f - BACKDROP_ALPHA) * fraction }
}

/**
 * The request deck's backdrop. In, it keeps the pace of the spring sharedBounds moves the card on.
 * Out, it runs stiffer: the pill lands in the app bar, which is behind the backdrop, and on the
 * matching spring the blur's tail would still be on the pill for a moment after it settles.
 */
internal fun deckBackdropSpec(expanded: Boolean): AnimationSpec<Float> =
    spring(stiffness = if (expanded) Spring.StiffnessMediumLow else Spring.StiffnessMedium)

private val BACKDROP_BLUR = 8.dp
private const val BACKDROP_ALPHA = 0.4f
