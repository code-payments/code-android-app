package com.getcode.codes.kikcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.PI

/**
 * Paints a [KikCodeDescription] onto an Android [Canvas].
 *
 * Deliberately thin: every coordinate is decided by the shared geometry, so this only turns marks
 * into draw calls. Keeping it that way is what lets the on-screen code, the exported PNG, the
 * exported SVG, and iOS all agree — the moment layout maths creeps back in here, they can drift.
 *
 * Paints are retained because this is used from `onDraw`.
 */
class KikCodePainter(color: Int = Color.WHITE) {

    /** Drawn into the centre well the geometry reserves; `null` leaves the well empty. */
    var badge: Drawable? = null

    var color: Int = color
        set(value) {
            field = value
            fillPaint.color = value
            strokePaint.color = value
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }

    private val arcBounds = RectF()

    fun draw(description: KikCodeDescription, canvas: Canvas) {
        // Runs are stroked, so the stroke width *is* the dot diameter -- a run of bits and an
        // isolated bit end up exactly the same thickness.
        strokePaint.strokeWidth = description.dotDiameter.toFloat()

        val center = description.center.toFloat()
        val dotRadius = (description.dotDiameter / 2.0).toFloat()

        description.marks.forEach { mark ->
            when (mark) {
                is KikCodeMark.Dot ->
                    canvas.drawCircle(mark.x.toFloat(), mark.y.toFloat(), dotRadius, fillPaint)

                is KikCodeMark.Ring ->
                    canvas.drawCircle(center, center, mark.radius.toFloat(), strokePaint)

                is KikCodeMark.Arc -> {
                    val radius = mark.radius.toFloat()
                    arcBounds.set(center - radius, center - radius, center + radius, center + radius)
                    canvas.drawArc(
                        arcBounds,
                        mark.startRadians.toDegrees(),
                        mark.sweepRadians.toDegrees(),
                        false,
                        strokePaint,
                    )
                }
            }
        }

        badge?.let { drawable ->
            val radius = description.badgeRadius
            drawable.setBounds(
                (description.center - radius).toInt(),
                (description.center - radius).toInt(),
                (description.center + radius).toInt(),
                (description.center + radius).toInt(),
            )
            drawable.draw(canvas)
        }
    }

    private fun Double.toDegrees(): Float = (this * 180.0 / PI).toFloat()
}

/**
 * Rasterises [payload] into a square [Bitmap] of [size] px.
 *
 * [background] defaults to transparent; pass an opaque colour when the destination can't handle
 * alpha. The caller owns the returned bitmap.
 */
fun kikCodeBitmap(
    payload: ByteArray,
    size: Int,
    badge: Drawable? = null,
    color: Int = Color.WHITE,
    background: Int = Color.TRANSPARENT,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (background != Color.TRANSPARENT) canvas.drawColor(background)
    KikCodePainter(color).apply { this.badge = badge }
        .draw(KikCodeGeometry.describe(payload, size.toDouble()), canvas)
    return bitmap
}
