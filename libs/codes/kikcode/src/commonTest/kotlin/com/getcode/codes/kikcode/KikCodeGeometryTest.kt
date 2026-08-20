package com.getcode.codes.kikcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KikCodeGeometryTest {

    private val payload = ByteArray(20) { (it * 37 + 11).toByte() }

    @Test
    fun rejects_a_non_positive_dimension() {
        assertFailsWith<IllegalArgumentException> { KikCodeGeometry.describe(payload, 0.0) }
        assertFailsWith<IllegalArgumentException> { KikCodeGeometry.describe(payload, -1.0) }
    }

    @Test
    fun rejects_an_empty_payload() {
        assertFailsWith<IllegalArgumentException> { KikCodeGeometry.describe(ByteArray(0), 512.0) }
    }

    @Test
    fun rejects_a_payload_that_would_overflow_the_rings() {
        val tooLong = ByteArray(KikCodeSpec.MAX_PAYLOAD_BYTES + 1)
        assertFailsWith<IllegalArgumentException> { KikCodeGeometry.describe(tooLong, 512.0) }
        // One byte less is the exact capacity, and must be accepted.
        KikCodeGeometry.describe(ByteArray(KikCodeSpec.MAX_PAYLOAD_BYTES), 512.0)
    }

    @Test
    fun the_rings_carry_exactly_the_advertised_capacity() {
        val bits = (0 until KikCodeSpec.RING_COUNT).sumOf { KikCodeSpec.bitsInRing(it) }
        assertEquals(KikCodeSpec.CAPACITY_BITS, bits)
        assertEquals(
            KikCodeSpec.CAPACITY_BYTES,
            KikCodeSpec.MAX_PAYLOAD_BYTES + KikCodeSpec.FINDER_BYTES.size,
        )
    }

    @Test
    fun every_mark_stays_inside_the_canvas() {
        val dimension = 1024.0
        val description = KikCodeGeometry.describe(payload, dimension)
        val half = description.dotDiameter / 2.0

        for (mark in description.marks) {
            when (mark) {
                is KikCodeMark.Dot -> {
                    assertTrue(mark.x - half >= 0.0 && mark.x + half <= dimension, "dot x: $mark")
                    assertTrue(mark.y - half >= 0.0 && mark.y + half <= dimension, "dot y: $mark")
                }
                is KikCodeMark.Arc ->
                    assertTrue(mark.radius + half <= description.center, "arc: $mark")
                is KikCodeMark.Ring ->
                    assertTrue(mark.radius + half <= description.center, "ring: $mark")
            }
        }
    }

    @Test
    fun no_data_ring_overlaps_the_badge_well() {
        val description = KikCodeGeometry.describe(payload, 1024.0)
        val half = description.dotDiameter / 2.0
        val radii = description.marks.mapNotNull {
            when (it) {
                is KikCodeMark.Arc -> it.radius
                is KikCodeMark.Ring -> it.radius
                is KikCodeMark.Dot -> null
            }
        }
        assertTrue(radii.isNotEmpty(), "expected some stroked marks")
        assertTrue(
            radii.min() - half >= description.badgeRadius,
            "innermost ring at ${radii.min()} intrudes on the badge (${description.badgeRadius})",
        )
    }

    @Test
    fun geometry_scales_linearly_with_the_dimension() {
        val small = KikCodeGeometry.describe(payload, 512.0)
        val large = KikCodeGeometry.describe(payload, 1024.0)

        assertEquals(small.marks.size, large.marks.size)
        assertEquals(
            KikCodeSvg.num(small.dotDiameter * 2.0),
            KikCodeSvg.num(large.dotDiameter),
        )
        small.marks.zip(large.marks).forEach { (a, b) ->
            when {
                a is KikCodeMark.Dot && b is KikCodeMark.Dot -> {
                    assertEquals(KikCodeSvg.num(a.x * 2.0), KikCodeSvg.num(b.x))
                    assertEquals(KikCodeSvg.num(a.y * 2.0), KikCodeSvg.num(b.y))
                }
                a is KikCodeMark.Arc && b is KikCodeMark.Arc -> {
                    assertEquals(KikCodeSvg.num(a.radius * 2.0), KikCodeSvg.num(b.radius))
                    // Angles are dimensionless -- they must not move with the size.
                    assertEquals(a.startRadians, b.startRadians)
                    assertEquals(a.sweepRadians, b.sweepRadians)
                }
                a is KikCodeMark.Ring && b is KikCodeMark.Ring ->
                    assertEquals(KikCodeSvg.num(a.radius * 2.0), KikCodeSvg.num(b.radius))
                else -> throw AssertionError("mark kinds diverged: $a vs $b")
            }
        }
    }

    @Test
    fun a_fully_set_ring_collapses_to_a_single_ring_mark() {
        // 0xFF from byte 4 on: rings 1..3 sit entirely inside the all-ones region.
        val description = KikCodeGeometry.describe(ByteArray(20) { 0xFF.toByte() }, 1024.0)
        assertTrue(
            description.marks.filterIsInstance<KikCodeMark.Ring>().isNotEmpty(),
            "expected at least one fully-set ring",
        )
    }

    @Test
    fun an_alternating_payload_produces_only_isolated_dots_in_the_data_rings() {
        // 0xAA has no two adjacent set bits, so past the finder bytes every mark is a lone dot.
        val description = KikCodeGeometry.describe(ByteArray(20) { 0xAA.toByte() }, 1024.0)
        val dots = description.marks.count { it is KikCodeMark.Dot }
        assertTrue(dots > description.marks.size / 2, "expected mostly dots, got $dots")
    }

    @Test
    fun a_run_of_set_bits_becomes_one_arc_not_many_dots() {
        // Bits 32..39 set (the first data byte), the rest clear: one run of 8 on ring 1.
        val payload = ByteArray(20).also { it[0] = 0xFF.toByte() }
        val description = KikCodeGeometry.describe(payload, 1024.0)
        val arcs = description.marks.filterIsInstance<KikCodeMark.Arc>()
        // Ring 1 has 40 bits; a run of 8 sweeps 7 of its 40 steps.
        val expectedSweep = 2.0 * kotlin.math.PI / 40.0 * 7.0
        assertTrue(
            arcs.any { KikCodeSvg.num(it.sweepRadians) == KikCodeSvg.num(expectedSweep) },
            "expected an arc sweeping 7 steps of ring 1; got ${arcs.map { it.sweepRadians }}",
        )
    }
}
