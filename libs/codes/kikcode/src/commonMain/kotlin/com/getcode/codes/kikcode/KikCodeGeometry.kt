package com.getcode.codes.kikcode

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A resolved, platform-free description of a Kik code graphic: where every mark goes, at what size.
 *
 * Produced once in shared code and consumed by every renderer — the Android `Canvas` painter, the
 * iOS `CGPath` painter, and [KikCodeSvg] — so all three draw byte-for-byte the same figure.
 *
 * All coordinates are in the same space as [dimension], with the origin at the graphic's top-left.
 */
data class KikCodeDescription(
    /** Side of the (square) graphic these coordinates are laid out in. */
    val dimension: Double,
    /** Center of the graphic, on both axes (`dimension / 2`). */
    val center: Double,
    /** Radius of the badge well at the middle, where the logo sits. */
    val badgeRadius: Double,
    /** Diameter of a dot, and equivalently the stroke width of an arc. */
    val dotDiameter: Double,
    /** Every mark to draw, innermost ring first, in ascending bit order within a ring. */
    val marks: List<KikCodeMark>,
)

/**
 * One drawable element of a code.
 *
 * A run of consecutive set bits collapses into a single [Arc]: stroked at [KikCodeDescription
 * .dotDiameter] with round caps, the arc's caps land exactly where the run's first and last dots
 * would, so the figure is identical to drawing every dot plus connecting bands — with far fewer
 * elements.
 */
sealed interface KikCodeMark {

    /** A lone set bit, with no set neighbour on either side. Filled, radius `dotDiameter / 2`. */
    data class Dot(val x: Double, val y: Double) : KikCodeMark

    /**
     * A run of two or more consecutive set bits, stroked with round caps.
     *
     * Angles are in radians, measured from the positive x-axis and increasing clockwise in a
     * y-down coordinate space (so `-PI / 2` is the apex of the circle). [sweepRadians] is always
     * positive and strictly less than `2 * PI`; a run that wraps past the ring's first bit simply
     * sweeps past it, so `startRadians + sweepRadians` may exceed `3 * PI / 2`.
     */
    data class Arc(
        val radius: Double,
        val startRadians: Double,
        val sweepRadians: Double,
    ) : KikCodeMark

    /** A ring whose every bit is set — a closed circle, which no single [Arc] can express. */
    data class Ring(val radius: Double) : KikCodeMark
}

/**
 * Computes [KikCodeDescription]s from a payload.
 *
 * The algorithm is the reference Kik code layout, previously reimplemented in
 * `KikCodeContentRendererImpl` (Android) and `KikCode.generateDescription` (iOS): six concentric
 * rings, ring `i` carrying `32 + 8i` bits read LSB-first out of [KikCodeSpec.FINDER_BYTES] followed
 * by the payload; a set bit is a mark at that bit's angle.
 */
object KikCodeGeometry {

    /**
     * Lays [payload] out in a [dimension] x [dimension] box.
     *
     * @throws IllegalArgumentException if [dimension] is not positive, or [payload] is empty or
     *   longer than [KikCodeSpec.MAX_PAYLOAD_BYTES].
     */
    fun describe(payload: ByteArray, dimension: Double): KikCodeDescription {
        require(dimension > 0.0) { "dimension must be positive, was $dimension" }
        require(payload.isNotEmpty()) { "payload is empty" }
        require(payload.size <= KikCodeSpec.MAX_PAYLOAD_BYTES) {
            "payload is ${payload.size} bytes; at most ${KikCodeSpec.MAX_PAYLOAD_BYTES} fit"
        }

        val bytes = KikCodeSpec.FINDER_BYTES + payload

        val center = dimension / 2.0
        val outerRadius = dimension * KikCodeSpec.OUTER_RATIO
        val badgeRadius = outerRadius * KikCodeSpec.INNER_RING_RATIO
        val firstRingEdge = outerRadius * KikCodeSpec.FIRST_RING_RATIO
        val lastRingEdge = outerRadius * KikCodeSpec.LAST_RING_RATIO
        val ringWidth = (lastRingEdge - firstRingEdge) / KikCodeSpec.RING_COUNT
        val dotDiameter = ringWidth * KikCodeSpec.DOT_RATIO

        val marks = mutableListOf<KikCodeMark>()
        var offset = 0

        for (ring in 0 until KikCodeSpec.RING_COUNT) {
            var innerEdge = ringWidth * ring + firstRingEdge
            // The innermost ring is nudged inward so it doesn't crowd the badge well.
            if (ring == 0) innerEdge -= badgeRadius / 10.0

            val bitCount = KikCodeSpec.bitsInRing(ring)
            val bits = BooleanArray(bitCount) { bitAt(bytes, offset + it) }
            addRingMarks(
                into = marks,
                bits = bits,
                radius = innerEdge + ringWidth / 2.0,
                center = center,
            )
            offset += bitCount
        }

        return KikCodeDescription(
            dimension = dimension,
            center = center,
            badgeRadius = badgeRadius,
            dotDiameter = dotDiameter,
            marks = marks,
        )
    }

    /** Collapses [bits] into the minimal set of marks on the ring at [radius]. */
    private fun addRingMarks(
        into: MutableList<KikCodeMark>,
        bits: BooleanArray,
        radius: Double,
        center: Double,
    ) {
        val n = bits.size
        val delta = 2.0 * PI / n

        // Fully set and fully clear rings have no run boundary to anchor the walk below.
        if (bits.all { it }) {
            into += KikCodeMark.Ring(radius)
            return
        }
        if (bits.none { it }) return

        for (index in 0 until n) {
            if (!bits[index]) continue
            // Only start at a run head, so each run is emitted exactly once. Because at least one
            // bit is clear, a run head always exists and every run is bounded.
            if (bits[(index - 1 + n) % n]) continue

            var length = 1
            while (bits[(index + length) % n]) length++

            val angle = angleOf(index, delta)
            into += if (length == 1) {
                KikCodeMark.Dot(
                    x = center + radius * cos(angle),
                    y = center + radius * sin(angle),
                )
            } else {
                KikCodeMark.Arc(
                    radius = radius,
                    startRadians = angle,
                    sweepRadians = delta * (length - 1),
                )
            }
        }
    }

    /** Angle of bit [index] on a ring of `2 * PI / delta` bits; bit 0 sits at the apex. */
    private fun angleOf(index: Int, delta: Double): Double = index * delta - PI / 2.0

    /** The [offset]-th bit of [bytes], LSB-first within each byte; `false` past the end. */
    private fun bitAt(bytes: ByteArray, offset: Int): Boolean {
        val byteIndex = offset / 8
        if (byteIndex >= bytes.size) return false
        return (bytes[byteIndex].toInt() and (1 shl (offset % 8))) != 0
    }
}
