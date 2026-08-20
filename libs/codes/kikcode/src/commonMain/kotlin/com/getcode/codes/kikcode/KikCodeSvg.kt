package com.getcode.codes.kikcode

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Serializes a code graphic to SVG.
 *
 * Entirely shared: SVG export needs no platform drawing at all, so Android and iOS emit identical
 * bytes for the same payload. Output is a standalone document with the badge embedded as a path --
 * no external assets, no fonts, no text.
 */
object KikCodeSvg {

    /** Default export size. Only affects the numbers in the file; SVG scales losslessly. */
    const val DEFAULT_DIMENSION: Double = 1024.0

    /**
     * Renders [payload] as a standalone SVG document.
     *
     * @param foreground CSS color for the code marks and badge.
     * @param background CSS color painted behind the code, or `null` for a transparent document.
     *   Codes are light-on-dark, so a transparent export is invisible on light surfaces -- pass the
     *   surface color the code is presented on.
     * @param includeBadge whether to embed the logo in the middle well.
     */
    fun render(
        payload: ByteArray,
        dimension: Double = DEFAULT_DIMENSION,
        foreground: String = "#FFFFFF",
        background: String? = null,
        includeBadge: Boolean = true,
    ): String = render(
        description = KikCodeGeometry.describe(payload, dimension),
        foreground = foreground,
        background = background,
        includeBadge = includeBadge,
    )

    /** Renders an already-computed [description]. */
    fun render(
        description: KikCodeDescription,
        foreground: String = "#FFFFFF",
        background: String? = null,
        includeBadge: Boolean = true,
    ): String = buildString {
        val side = num(description.dimension)
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(side)
        append("\" height=\"").append(side)
        append("\" viewBox=\"0 0 ").append(side).append(' ').append(side).append("\">\n")

        if (background != null) {
            append("<rect width=\"").append(side).append("\" height=\"").append(side)
            append("\" fill=\"").append(background).append("\"/>\n")
        }

        appendDots(description, foreground)
        appendStrokes(description, foreground)
        if (includeBadge) appendBadge(description, foreground)

        append("</svg>\n")
    }

    private fun StringBuilder.appendDots(description: KikCodeDescription, foreground: String) {
        val dots = description.marks.filterIsInstance<KikCodeMark.Dot>()
        if (dots.isEmpty()) return

        val radius = num(description.dotDiameter / 2.0)
        append("<g fill=\"").append(foreground).append("\">\n")
        for (dot in dots) {
            append("<circle cx=\"").append(num(dot.x))
            append("\" cy=\"").append(num(dot.y))
            append("\" r=\"").append(radius).append("\"/>\n")
        }
        append("</g>\n")
    }

    /**
     * Arcs and full rings share one stroked group: both are centerline paths widened to
     * [KikCodeDescription.dotDiameter] with round caps, which is what makes a run of bits read as a
     * capsule with dot-shaped ends.
     */
    private fun StringBuilder.appendStrokes(description: KikCodeDescription, foreground: String) {
        val strokes = description.marks.filter { it !is KikCodeMark.Dot }
        if (strokes.isEmpty()) return

        append("<g fill=\"none\" stroke=\"").append(foreground)
        append("\" stroke-width=\"").append(num(description.dotDiameter))
        append("\" stroke-linecap=\"round\">\n")

        val center = description.center
        for (mark in strokes) {
            when (mark) {
                is KikCodeMark.Ring -> {
                    append("<circle cx=\"").append(num(center))
                    append("\" cy=\"").append(num(center))
                    append("\" r=\"").append(num(mark.radius)).append("\"/>\n")
                }

                is KikCodeMark.Arc -> {
                    val endAngle = mark.startRadians + mark.sweepRadians
                    val radius = num(mark.radius)
                    // Sweep flag 1: angles increase clockwise, matching the y-down layout space.
                    val largeArc = if (mark.sweepRadians > PI) 1 else 0
                    append("<path d=\"M ")
                    append(num(center + mark.radius * cos(mark.startRadians))).append(' ')
                    append(num(center + mark.radius * sin(mark.startRadians)))
                    append(" A ").append(radius).append(' ').append(radius)
                    append(" 0 ").append(largeArc).append(" 1 ")
                    append(num(center + mark.radius * cos(endAngle))).append(' ')
                    append(num(center + mark.radius * sin(endAngle)))
                    append("\"/>\n")
                }

                is KikCodeMark.Dot -> Unit // Filtered out above; drawn filled, not stroked.
            }
        }
        append("</g>\n")
    }

    private fun StringBuilder.appendBadge(description: KikCodeDescription, foreground: String) {
        val diameter = description.badgeRadius * 2.0
        val scale = diameter / KikCodeBadge.VIEWPORT
        val origin = description.center - description.badgeRadius

        append("<g fill=\"").append(foreground).append("\" fill-rule=\"evenodd\" transform=\"")
        append("translate(").append(num(origin)).append(' ').append(num(origin)).append(") ")
        append("scale(").append(num(scale)).append(")\">\n")
        append("<path d=\"").append(KikCodeBadge.PATH_DATA).append("\"/>\n")
        append("</g>\n")
    }

    /**
     * Formats a coordinate deterministically.
     *
     * `Double.toString()` is not specified to agree between Kotlin/JVM and Kotlin/Native, and the
     * geometry runs through `cos`/`sin`, whose last-place results may differ between platform libms.
     * Rounding to [DECIMALS] places -- far coarser than any such difference, and finer than a pixel
     * at any sane export size -- makes the emitted document byte-identical on both.
     *
     * `roundToLong` (ties toward positive infinity), not `round`, because only the former has a
     * specified tie-break; `round`'s ties-to-even would be a second source of platform drift.
     */
    internal fun num(value: Double): String {
        val scaled = (value * SCALE).roundToLong()
        if (scaled == 0L) return "0"

        val negative = scaled < 0
        val magnitude = if (negative) -scaled else scaled
        val fraction = (magnitude % SCALE_LONG).toString()
            .padStart(DECIMALS, '0')
            .trimEnd('0')

        return buildString {
            if (negative) append('-')
            append(magnitude / SCALE_LONG)
            if (fraction.isNotEmpty()) append('.').append(fraction)
        }
    }

    private const val DECIMALS = 3
    private const val SCALE = 1000.0
    private const val SCALE_LONG = 1000L
}
