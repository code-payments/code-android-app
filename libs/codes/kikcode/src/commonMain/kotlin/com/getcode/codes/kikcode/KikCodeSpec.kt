package com.getcode.codes.kikcode

/**
 * The canonical Kik-code drawing spec — the single source of truth both apps derive their code
 * graphic from.
 *
 * These constants previously lived twice: Android's `KikCodeContentRendererImpl` and iOS's
 * `KikCode.swift`. The ratios agreed; the *frame* they were applied to did not (see [OUTER_RATIO]).
 */
object KikCodeSpec {

    /** Number of concentric data rings. */
    const val RING_COUNT: Int = 6

    /** Additional bits each successive ring carries over the innermost ring's 32. */
    const val BITS_PER_RING_STEP: Int = 8

    /** Bits carried by the innermost ring. */
    const val BASE_BITS_PER_RING: Int = 32

    /**
     * Radius of the badge/logo well, as a fraction of the code's outer radius.
     * Also the diameter fraction of the whole graphic (`2 * 0.32 * 0.5 == 0.32`).
     */
    const val INNER_RING_RATIO: Double = 0.32

    /** Inner edge of the first data ring, as a fraction of the outer radius. */
    const val FIRST_RING_RATIO: Double = 0.425

    /** Outer edge of the last data ring, as a fraction of the outer radius. */
    const val LAST_RING_RATIO: Double = 0.95

    /** Dot diameter (and arc stroke width) as a fraction of a single ring's width. */
    const val DOT_RATIO: Double = 0.75

    /**
     * The code's outer radius as a fraction of the graphic's smaller side.
     *
     * Canonically `0.5` — the code fills its box, and the outermost dots still clear the edge by
     * ~3% because [LAST_RING_RATIO] already reserves that margin.
     *
     * Note this adopts iOS's framing. Android previously computed `size / 2 * 0.93` and then had
     * `KikCodeContentView.onDraw` scale the render size up by `1.03` to compensate — a net `0.958`.
     * Adopting the canonical value makes the Android graphic ~4.2% larger within the same box, and
     * grows the badge well to match iOS (`0.298` -> `0.32` of the graphic).
     */
    const val OUTER_RATIO: Double = 0.5

    /** Fixed prefix every payload is drawn with, so scanners can lock onto the code. */
    val FINDER_BYTES: ByteArray = byteArrayOf(0xB2.toByte(), 0xCB.toByte(), 0x25.toByte(), 0xC6.toByte())

    /**
     * Total bits the six rings can carry: `32 + 40 + 48 + 56 + 64 + 72`.
     */
    const val CAPACITY_BITS: Int = 312

    /** Total bytes (finder bytes included) the rings can carry: `312 / 8`. */
    const val CAPACITY_BYTES: Int = CAPACITY_BITS / 8

    /** Largest caller payload that fits once [FINDER_BYTES] is prepended. */
    const val MAX_PAYLOAD_BYTES: Int = CAPACITY_BYTES - 4

    /** Bits carried by ring [index] (0-based, innermost first). */
    fun bitsInRing(index: Int): Int = BASE_BITS_PER_RING + BITS_PER_RING_STEP * index
}
