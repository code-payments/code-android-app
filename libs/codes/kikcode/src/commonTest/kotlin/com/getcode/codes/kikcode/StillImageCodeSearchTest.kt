package com.getcode.codes.kikcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ladder is mirrored by hand in `StillImageCodeSearch.swift`. These assertions are the
 * contract both copies answer to — the same reason [LuminancePlaneTest] exists.
 */
class StillImageCodeSearchTest {

    @Test
    fun `first candidate is the whole image at zoom one`() {
        val first = StillImageCodeSearch.candidates(1920, 1080).first()

        assertEquals(0, first.left)
        assertEquals(0, first.top)
        assertEquals(1920, first.width)
        assertEquals(1080, first.height)
        assertEquals(1f, first.zoom)
    }

    @Test
    fun `every candidate lies inside the image`() {
        StillImageCodeSearch.candidates(1920, 1080).forEach { candidate ->
            assertTrue(candidate.left >= 0 && candidate.top >= 0, "negative origin: $candidate")
            assertTrue(candidate.left + candidate.width <= 1920, "overruns width: $candidate")
            assertTrue(candidate.top + candidate.height <= 1080, "overruns height: $candidate")
        }
    }

    @Test
    fun `a crop is never rendered larger than the cap`() {
        StillImageCodeSearch.candidates(1920, 1080).forEach { candidate ->
            // Tier 1 is exempt, the same exemption `quadrants stop subdividing at the
            // minimum section size` makes: the whole image is enumerated unconditionally so
            // that every image gets a first pass, and the caller clamps it when it renders.
            val isWholeImage = candidate.width == 1920 && candidate.height == 1080
            val longest = maxOf(candidate.width, candidate.height) * candidate.zoom
            assertTrue(
                isWholeImage || longest <= StillImageCodeSearch.MAX_RENDERED_SIDE,
                "$candidate renders to $longest"
            )
        }
    }

    @Test
    fun `quadrants stop subdividing at the minimum section size`() {
        StillImageCodeSearch.candidates(1920, 1080).forEach { candidate ->
            val isWholeImage = candidate.width == 1920 && candidate.height == 1080
            val shortest = minOf(candidate.width, candidate.height)
            assertTrue(
                isWholeImage || shortest >= StillImageCodeSearch.MIN_SECTION_SIZE,
                "section too small: $candidate"
            )
        }
    }

    @Test
    fun `the same crop is never enumerated twice at the same zoom`() {
        val candidates = StillImageCodeSearch.candidates(1920, 1080)

        // Compared on the crop and its zoom rather than on the whole candidate: `tier` is part of
        // the data class, so `toSet()` alone would let a window that repeats a quadrant through.
        val crops = candidates.map { listOf(it.left, it.top, it.width, it.height, it.zoom) }

        assertEquals(crops.size, crops.toSet().size, "duplicate candidates")
    }

    @Test
    fun `each candidate carries the tier that enumerated it`() {
        val candidates = StillImageCodeSearch.candidates(1920, 1080)

        assertEquals(1, candidates.first().tier, "the whole image is tier 1")
        assertEquals(1, candidates.count { it.tier == 1 }, "only the whole image is tier 1")

        // Tiers are enumerated in order, so a candidate never follows one from a later tier.
        val tiers = candidates.map { it.tier }
        assertEquals(tiers.sorted(), tiers, "tiers are out of order")

        assertTrue(candidates.any { it.tier == 2 }, "no quadrant was enumerated")
        candidates.filter { it.tier == 3 }.forEach {
            assertEquals(StillImageCodeSearch.WINDOW_SIZE, it.width, "$it is not a window")
            assertEquals(StillImageCodeSearch.WINDOW_SIZE, it.height, "$it is not a window")
        }
    }

    @Test
    fun `an image smaller than one window still yields the whole image`() {
        val candidates = StillImageCodeSearch.candidates(80, 60)

        assertTrue(candidates.isNotEmpty())
        assertEquals(80, candidates.first().width)
        assertEquals(60, candidates.first().height)
    }
}
