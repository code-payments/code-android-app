package com.flipcash.shared.chat.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The avatar column an incoming row of a group reserves beside its bubble. */
val SenderGutterWidth: Dp = 25.dp

/**
 * How wide, in px, a photo bubble draws in a transcript on a screen [screenWidthPx] wide, worked out
 * without composing it, so a prefetch can pick the blob the bubble will ask for.
 *
 * Follows the layout the transcript composes: the list insets each row by the theme's inset, an
 * incoming row of a group gives [SenderGutterWidth] and one grid step to the avatar column, and the
 * bubble takes [transcriptBubbleMaxWidth] of what is left. Each length rounds to whole px as layout
 * rounds it. `PhotoPrefetchRenditionTest` checks the result against the composed row.
 */
fun photoBubbleWidthPx(screenWidthPx: Int, density: Float, groupIncoming: Boolean): Int =
    with(Density(density)) {
        val screenDp = screenWidthPx / density
        // The theme's inset and first grid step for this width, as `calculateDimensions` sets them.
        val (inset, gridStep) = when {
            screenDp <= 320 -> 10.dp to 2.5.dp
            screenDp <= 600 -> 20.dp to 5.dp
            else -> 30.dp to 7.5.dp
        }
        var rowPx = screenWidthPx - 2 * inset.roundToPx()
        if (groupIncoming) rowPx -= SenderGutterWidth.roundToPx() + gridStep.roundToPx()
        transcriptBubbleMaxWidth(rowPx.toDp(), inset, fillsWidth = true).roundToPx()
    }
