package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.TranscriptDrag
import com.flipcash.app.messenger.internal.screens.components.classifyTranscriptDrag
import com.flipcash.app.messenger.internal.screens.components.revealOffset
import com.flipcash.app.messenger.internal.screens.components.startsInBackEdge
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The geometry and the direction gate of the transcript's send-time reveal, in pixels: a 76 shift
 * at full reveal (the 64 column and its 12 margin), and an 8 touch slop.
 *
 * iOS uses the same curve, so a change to the offset here is a change on both platforms.
 */
class TimestampRevealTest {

    private val shift = 76f
    private val slop = 8f
    private val tolerance = 0.01f

    @Test
    fun `no travel leaves the column closed`() {
        assertEquals(0f, revealOffset(0f, shift), tolerance)
        assertEquals(0f, revealOffset(-40f, shift), tolerance)
    }

    @Test
    fun `for the first half of the column the bubbles follow the finger exactly`() {
        assertEquals(20f, revealOffset(20f, shift), tolerance)
        assertEquals(shift / 2, revealOffset(shift / 2, shift), tolerance)
    }

    @Test
    fun `past half the column the drag resists`() {
        val offset = revealOffset(shift, shift)
        assertTrue(offset > shift / 2 && offset < shift, "offset $offset")
    }

    @Test
    fun `however hard the drag the offset never passes the column`() {
        assertTrue(revealOffset(10_000f, shift) <= shift)
        assertTrue(revealOffset(10_000f, shift) > shift * 0.99f)
    }

    @Test
    fun `the offset only grows with travel`() {
        var last = 0f
        for (travel in 0..400 step 5) {
            val offset = revealOffset(travel.toFloat(), shift)
            assertTrue(offset >= last, "at $travel: $offset < $last")
            last = offset
        }
    }

    @Test
    fun `a drag is undecided inside the slop`() {
        assertEquals(TranscriptDrag.Undecided, classifyTranscriptDrag(-slop, slop, slop))
    }

    @Test
    fun `a leading-ward drag reveals and a trailing-ward one is the reply swipe's`() {
        assertEquals(TranscriptDrag.Reveal, classifyTranscriptDrag(-slop - 1, 0f, slop))
        assertEquals(TranscriptDrag.Reply, classifyTranscriptDrag(slop + 1, 0f, slop))
    }

    @Test
    fun `a drag that crosses the vertical slop first scrolls`() {
        assertEquals(TranscriptDrag.Scroll, classifyTranscriptDrag(-4f, slop + 1, slop))
        assertEquals(TranscriptDrag.Scroll, classifyTranscriptDrag(-4f, -slop - 1, slop))
    }

    @Test
    fun `a drag from the back gesture strip is left to the system`() {
        assertTrue(startsInBackEdge(windowX = 1060f, windowWidthPx = 1080f, backEdgePx = 30f))
        assertTrue(startsInBackEdge(windowX = 1050f, windowWidthPx = 1080f, backEdgePx = 30f))
        assertFalse(startsInBackEdge(windowX = 1049f, windowWidthPx = 1080f, backEdgePx = 30f))
    }

    @Test
    fun `with no gesture strip every drag can reveal`() {
        assertFalse(startsInBackEdge(windowX = 1079f, windowWidthPx = 1080f, backEdgePx = 0f))
    }
}
