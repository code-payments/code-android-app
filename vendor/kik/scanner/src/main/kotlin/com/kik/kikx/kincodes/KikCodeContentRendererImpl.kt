package com.kik.kikx.kincodes

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.getcode.codes.kikcode.KikCodeGeometry
import com.getcode.codes.kikcode.KikCodePainter

/**
 * Draws a scannable code by delegating its layout to the shared (KMP) geometry and painting the
 * resulting marks.
 *
 * This used to compute the ring radii and walk the bits itself, which meant the on-screen code, the
 * exported images, and iOS each had their own copy of the maths. The geometry now lives in
 * `:libs:codes:kikcode` and is gated by cross-platform vectors, so all four surfaces are the same
 * numbers.
 */
class KikCodeContentRendererImpl : KikCodeContentRenderer {

    private val painter = KikCodePainter()

    var badge: Drawable?
        get() = painter.badge
        set(value) {
            painter.badge = value
        }

    override fun render(encodedKikCode: ByteArray, size: Int, canvas: Canvas) {
        if (encodedKikCode.isEmpty()) return
        painter.draw(KikCodeGeometry.describe(encodedKikCode, size.toDouble()), canvas)
    }
}
