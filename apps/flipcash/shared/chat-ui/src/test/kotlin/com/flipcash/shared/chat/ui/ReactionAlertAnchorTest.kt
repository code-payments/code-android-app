package com.flipcash.shared.chat.ui

import android.graphics.Rect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReactionAlertAnchorTest {
    private val width = 1080

    @Test
    fun `no cutout falls back to the banner`() {
        assertEquals(ReactionAlertAnchor.Banner, reactionAlertAnchor(emptyList(), width))
    }

    @Test
    fun `centred punch hole anchors centred`() {
        val anchor = reactionAlertAnchor(listOf(Rect(500, 40, 580, 120)), width)
        assertIs<ReactionAlertAnchor.Centered>(anchor)
    }

    @Test
    fun `corner hole at the start grows inward from its left edge`() {
        val anchor = reactionAlertAnchor(listOf(Rect(40, 40, 120, 120)), width)
        assertIs<ReactionAlertAnchor.Corner>(anchor)
        assertTrue(anchor.atStart)
    }

    @Test
    fun `corner hole at the end anchors the right edge`() {
        val anchor = reactionAlertAnchor(listOf(Rect(960, 40, 1040, 120)), width)
        assertIs<ReactionAlertAnchor.Corner>(anchor)
        assertEquals(false, anchor.atStart)
    }

    @Test
    fun `side and bottom cutouts are not anchors`() {
        assertEquals(ReactionAlertAnchor.Banner, reactionAlertAnchor(listOf(Rect(0, 900, 80, 1000)), width))
        assertEquals(ReactionAlertAnchor.Banner, reactionAlertAnchor(listOf(Rect(500, 2300, 580, 2400)), width))
    }
}
