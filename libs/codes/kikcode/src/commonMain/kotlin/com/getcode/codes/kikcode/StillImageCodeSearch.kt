package com.getcode.codes.kikcode

/**
 * The order in which a still image is searched for a Kik code.
 *
 * A camera frame needs none of this: the user aims, so the code is roughly centred and
 * roughly the right size. A picked photo is whatever someone captured, and `scanKikCode`
 * reads at a fixed scale, so a small code has to be cropped out and scaled up before the
 * scanner can see it.
 *
 * Here rather than in the scanner module because iOS reimplements it by hand — the same
 * arrangement as [LuminancePlane], and for the same reason: two copies that must produce
 * the same crops in the same order, held together by a test both sides run.
 *
 * The constants come from Code's `processBitmapRecursively` and `slidingWindowSearch`
 * (`f81d064d0`). They have never been measured against a corpus of real screenshots — see
 * the spec's open decisions.
 */
object StillImageCodeSearch {

    /** A crop in the image's own pixel coordinates, and how much to scale it before scanning. */
    data class Candidate(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val zoom: Float,
        /**
         * Which tier enumerated this crop: 1 whole image, 2 quadrant, 3 sliding window.
         *
         * Carried so a caller can report how deep a successful search had to go. It is
         * deliberately outside the dedup key below -- a window that lands exactly on a quadrant
         * is still the same crop, and enumerating it twice under two tier numbers would change
         * the search itself to measure it.
         */
        val tier: Int,
    )

    /**
     * A quadrant is not subdivided below this, because a code smaller than this in the
     * original is past the point where scaling recovers it.
     */
    const val MIN_SECTION_SIZE = 100

    const val WINDOW_SIZE = 300
    const val WINDOW_STEP = 150

    /**
     * The longest side any crop is rendered at. This is what makes [Candidate.zoom] mean
     * something: a large crop has no room to grow and is scanned once, while a small one
     * gets the whole ladder. Without the cap, zoom 10 on a half-frame quadrant would render
     * a 9600-pixel bitmap to look for a code that is already plainly visible.
     *
     * Tier 1 is the exception: the whole image is always tried, whatever its size, so the
     * caller clamps it rather than the enumeration dropping it.
     */
    const val MAX_RENDERED_SIDE = 1600

    val ZOOM_LEVELS = listOf(1f, 2f, 5f, 10f)

    /**
     * Every crop the search will try, in the order it will try them.
     *
     * Tier 1 is the whole image, which is the only tier that runs for a screenshot where the
     * code fills the frame. Tiers 2 and 3 are the expensive ones and exist for a photo of a
     * screen across a room.
     */
    fun candidates(imageWidth: Int, imageHeight: Int): List<Candidate> {
        val candidates = mutableListOf(Candidate(0, 0, imageWidth, imageHeight, 1f, tier = 1))
        val seen = mutableSetOf(candidates.first().key)

        subdivide(0, 0, imageWidth, imageHeight, candidates, seen)
        window(imageWidth, imageHeight, candidates, seen)

        return candidates
    }

    private fun subdivide(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        candidates: MutableList<Candidate>,
        seen: MutableSet<Key>,
    ) {
        val halfWidth = width / 2
        val halfHeight = height / 2

        if (minOf(halfWidth, halfHeight) < MIN_SECTION_SIZE) return

        for (row in 0 until 2) {
            for (column in 0 until 2) {
                val quadrantLeft = left + column * halfWidth
                val quadrantTop = top + row * halfHeight

                add(quadrantLeft, quadrantTop, halfWidth, halfHeight, 2, candidates, seen)
                subdivide(quadrantLeft, quadrantTop, halfWidth, halfHeight, candidates, seen)
            }
        }
    }

    private fun window(
        imageWidth: Int,
        imageHeight: Int,
        candidates: MutableList<Candidate>,
        seen: MutableSet<Key>,
    ) {
        if (imageWidth < WINDOW_SIZE || imageHeight < WINDOW_SIZE) return

        var top = 0
        while (top + WINDOW_SIZE <= imageHeight) {
            var left = 0
            while (left + WINDOW_SIZE <= imageWidth) {
                add(left, top, WINDOW_SIZE, WINDOW_SIZE, 3, candidates, seen)
                left += WINDOW_STEP
            }
            top += WINDOW_STEP
        }
    }

    /**
     * Adds a crop at every zoom that fits under [MAX_RENDERED_SIDE], skipping any crop and
     * zoom pair an earlier tier already enumerated.
     */
    private fun add(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        tier: Int,
        candidates: MutableList<Candidate>,
        seen: MutableSet<Key>,
    ) {
        val longestSide = maxOf(width, height)

        ZOOM_LEVELS
            .filter { longestSide * it <= MAX_RENDERED_SIDE }
            .forEach { zoom ->
                val candidate = Candidate(left, top, width, height, zoom, tier)
                if (seen.add(candidate.key)) candidates += candidate
            }
    }

    /** A candidate's identity for dedup: the crop and its zoom, without the tier that found it. */
    private data class Key(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val zoom: Float,
    )

    private val Candidate.key: Key
        get() = Key(left, top, width, height, zoom)
}
