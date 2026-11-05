package com.flipcash.shared.chat.ui.media

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fan of a card stack fits inside the box a single photo of the same image takes. */
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
    fun `every tilted card stays inside the box, wide or tall`() {
        // A landscape photo, a square one and one clipped at the 5:4 tall cap.
        for ((width, height) in listOf(260f to 195f, 260f to 260f, 260f to 325f)) {
            for (behind in 1..2) {
                val poses = PhotoStackGeometry.poses(behind, nearNudge = 4f, farNudge = 8f)
                val scale = PhotoStackGeometry.cardScale(width, height, poses)
                assertTrue(scale < 1f, "the cards shrink to leave the fan room")
                val w = width * scale
                val h = height * scale
                for (pose in poses) {
                    val r = Math.toRadians(pose.tiltDegrees.toDouble())
                    val across = (w * abs(cos(r)) + h * abs(sin(r))).toFloat() / 2f + abs(pose.nudge)
                    val down = (w * abs(sin(r)) + h * abs(cos(r))).toFloat() / 2f
                    assertTrue(across <= width / 2f + 0.01f, "$width x $height, $behind behind: reaches $across across")
                    assertTrue(down <= height / 2f + 0.01f, "$width x $height, $behind behind: reaches $down down")
                }
            }
        }
    }

    @Test
    fun `nothing behind draws the card at full size`() {
        assertEquals(1f, PhotoStackGeometry.cardScale(260f, 195f, emptyList()))
    }

    @Test
    fun `the stack is no taller than the photo, and a tall photo's stack is shorter`() {
        // The width binds for a tall photo, so its cards shrink further than its height needs and the
        // box drops to the fan's height: no empty band above or below for the next message to clear.
        for ((width, height) in listOf(260f to 195f, 260f to 260f, 260f to 325f)) {
            for (behind in 1..2) {
                val poses = PhotoStackGeometry.poses(behind, nearNudge = 4f, farNudge = 8f)
                val scale = PhotoStackGeometry.cardScale(width, height, poses)
                val stack = PhotoStackGeometry.stackHeight(width * scale, height * scale, poses)
                assertTrue(stack <= height + 0.01f, "$width x $height, $behind behind: $stack tall")
                assertTrue(stack >= height * scale, "$width x $height, $behind behind: shorter than its card")
            }
        }
        val tall = PhotoStackGeometry.poses(1, nearNudge = 4f, farNudge = 8f)
        val scale = PhotoStackGeometry.cardScale(260f, 346.7f, tall)
        assertTrue(PhotoStackGeometry.stackHeight(260f * scale, 346.7f * scale, tall) < 346.7f - 1f)
    }
}
