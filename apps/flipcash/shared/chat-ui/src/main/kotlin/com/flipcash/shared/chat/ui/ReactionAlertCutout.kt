package com.flipcash.shared.chat.ui

import android.graphics.Rect

/** Where the pill grows out of, derived from `DisplayCutout.getBoundingRects()`. */
sealed interface ReactionAlertAnchor {
    /** No top cutout: the pill drops from the top as a banner. */
    data object Banner : ReactionAlertAnchor

    /** A cutout the pill is centred on (a punch-hole camera or a notch). */
    data class Centered(val rect: Rect) : ReactionAlertAnchor

    /** A corner hole: the pill's near edge is fixed to the hole, so it grows inward. */
    data class Corner(val rect: Rect, val atStart: Boolean) : ReactionAlertAnchor
}

/**
 * Picks the top cutout. Rects on the sides or bottom (a landscape notch, a bottom hole) are not
 * anchors; with several top rects the one nearest the top edge wins.
 */
fun reactionAlertAnchor(rects: List<Rect>, screenWidthPx: Int): ReactionAlertAnchor {
    val top = rects
        .filter { it.width() > 0 && it.height() > 0 && it.top <= it.height() }
        .minByOrNull { it.top }
        ?: return ReactionAlertAnchor.Banner
    val centreX = (top.left + top.right) / 2f
    val offCentre = kotlin.math.abs(centreX - screenWidthPx / 2f)
    return if (offCentre <= screenWidthPx * CENTRED_TOLERANCE) {
        ReactionAlertAnchor.Centered(top)
    } else {
        ReactionAlertAnchor.Corner(top, atStart = centreX < screenWidthPx / 2f)
    }
}

private const val CENTRED_TOLERANCE = 0.1f
