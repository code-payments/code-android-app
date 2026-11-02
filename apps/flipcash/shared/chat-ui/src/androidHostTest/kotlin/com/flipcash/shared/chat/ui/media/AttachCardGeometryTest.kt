package com.flipcash.shared.chat.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachCardGeometryTest {
    @Test
    fun `card is 58 percent of the screen, rounded`() {
        assertEquals(1393f, cardHeight(screenHeightPx = 2402f, fallbackPx = 480f))
    }

    @Test
    fun `an unmeasured screen falls back`() {
        assertEquals(480f, cardHeight(screenHeightPx = 0f, fallbackPx = 480f))
    }

    @Test
    fun `card stands inset from the sides and above the bottom inset`() {
        val rect = cardRect(width = 1200f, height = 2400f, insetPx = 30f, bottomInsetPx = 80f, heightPx = 1392f)
        assertEquals(30f, rect.left)
        assertEquals(1170f, rect.right)
        assertEquals(2320f, rect.bottom)
        assertEquals(928f, rect.top)
    }

    @Test
    fun `card never grows taller than the room above the bottom inset`() {
        val rect = cardRect(width = 1200f, height = 400f, insetPx = 30f, bottomInsetPx = 80f, heightPx = 1000f)
        assertEquals(0f, rect.top)
        assertEquals(320f, rect.bottom)
    }

    // Seeker: 1200 x 2670, 12dp = 36px, nav bar 72px. The card does not depend on the keyboard.
    @Test
    fun `card spans the full width with equal insets and covers the composer`() {
        val rect = cardRect(width = 1200f, height = 2670f, insetPx = 24f, bottomInsetPx = 24f + 72f, heightPx = cardHeight(2670f, 480f))
        assertEquals(24f, rect.left)
        assertEquals(1200f - 24f, rect.right)
        assertEquals(2670f - 96f, rect.bottom)
        assertEquals(1549f, rect.height)
    }

    @Test
    fun `menu above the bottom is left alone`() {
        val menu = androidx.compose.ui.geometry.Rect(39f, 1400f, 699f, 1830f)
        assertEquals(menu, menuClampedAbove(menu, limitBottom = 2526f))
    }

    @Test
    fun `menu is lifted onto the screen when the keyboard is down`() {
        val menu = androidx.compose.ui.geometry.Rect(39f, 2330f, 699f, 2760f)
        val lifted = menuClampedAbove(menu, limitBottom = 2526f)
        assertEquals(2526f, lifted.bottom)
        assertEquals(2096f, lifted.top)
        assertEquals(menu.width, lifted.width)
    }

    @Test
    fun `menu limit is the composer bottom with the keyboard down and the screen with it up`() {
        assertEquals(1700f, menuLimitBottom(screenBottom = 2526f, composerBottom = 1700f, keyboardUp = false))
        assertEquals(2526f, menuLimitBottom(screenBottom = 2526f, composerBottom = 1700f, keyboardUp = true))
    }
}
