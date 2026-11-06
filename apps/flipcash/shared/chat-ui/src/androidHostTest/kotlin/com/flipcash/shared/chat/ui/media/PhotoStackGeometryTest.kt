package com.flipcash.shared.chat.ui.media

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A card stack takes a square frame, and its fan fits inside it. */
class PhotoStackGeometryTest {

    @Test
    fun `one card behind per photo after the first, at most two`() {
        assertEquals(1, PhotoStackLook.behindCount(2))
        assertEquals(2, PhotoStackLook.behindCount(3))
        assertEquals(2, PhotoStackLook.behindCount(5))
        assertEquals(1, PhotoStackGeometry.poses(1, nearNudge = 4f, farNudge = 8f).size)
        assertEquals(2, PhotoStackGeometry.poses(2, nearNudge = 4f, farNudge = 8f).size)
    }

    @Test
    fun `a stack's frame is a maxWidth square whatever its photos' shape`() {
        assertEquals(ChatMediaBubbleSize(260f, 260f, cropped = true), ChatMediaBubbleSizing.stackSize(260f))
    }

    @Test
    fun `every turned card stays inside the square and just meets an edge`() {
        for (side in listOf(200f, 260f, 440f)) {
            for (behind in 1..2) {
                val poses = PhotoStackGeometry.poses(behind, nearNudge = 4f, farNudge = 8f)
                val fan = assertNotNull(PhotoStackGeometry.fan(side, side, poses))
                assertTrue(fan.frontWidth < side, "the front narrows to leave the fan room")
                assertTrue(fan.cardHeight < side, "the back cards shorten to fit turned")
                var tightest = Float.MAX_VALUE
                for (pose in poses) {
                    val r = Math.toRadians(pose.tiltDegrees.toDouble())
                    val across = (fan.frontWidth * abs(cos(r)) + fan.cardHeight * abs(sin(r))).toFloat() / 2f + abs(pose.nudge)
                    val down = (fan.frontWidth * abs(sin(r)) + fan.cardHeight * abs(cos(r))).toFloat() / 2f
                    assertTrue(across <= side / 2f + 0.01f, "$side, $behind behind: reaches $across across")
                    assertTrue(down <= side / 2f + 0.01f, "$side, $behind behind: reaches $down down")
                    tightest = minOf(tightest, side / 2f - across, side / 2f - down)
                }
                assertTrue(tightest < 0.01f, "$side, $behind behind: no card meets an edge")
            }
        }
    }

    @Test
    fun `without a nudge the fan matches iOS's stackFan`() {
        // iOS: width = (W cos - H sin) / (cos² - sin²), card = (H cos - W sin) / (cos² - sin²), at 4°.
        val r = Math.toRadians(4.0)
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val det = c * c - s * s
        val fan = assertNotNull(PhotoStackGeometry.fan(260f, 260f, listOf(StackCardPose(-4f, 0f))))
        assertEquals((260f * c - 260f * s) / det, fan.frontWidth, 0.01f)
        assertEquals((260f * c - 260f * s) / det, fan.cardHeight, 0.01f)
    }

    @Test
    fun `nothing behind fills the frame, and a frame too small to fan gives null`() {
        assertEquals(StackFan(260f, 260f), PhotoStackGeometry.fan(260f, 260f, emptyList()))
        assertNull(PhotoStackGeometry.fan(10f, 10f, PhotoStackGeometry.poses(2, nearNudge = 4f, farNudge = 8f)))
    }
}
