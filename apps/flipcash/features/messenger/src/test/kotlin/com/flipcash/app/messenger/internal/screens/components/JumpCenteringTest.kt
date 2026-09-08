package com.flipcash.app.messenger.internal.screens.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The jump centres the message it lands on in one scroll, and the whole of that scroll is one
 * number: the `scrollOffset` handed to `animateScrollToItem`, which positions the target's leading
 * edge at `-scrollOffset` from the start of the band.
 *
 * The band is what is left of the viewport once content padding is off it.
 */
class JumpCenteringTest {

    private val band = 1000 // a 1000px band

    @Test
    fun `a message is offset by half the room it leaves over`() {
        // 200px in a 1000px band leaves 800, so its leading edge goes 400 in and its middle at 500.
        assertEquals(-400, centeringOffset(bandHeight = band, itemHeight = 200))
    }

    @Test
    fun `a message filling half the band still centres`() {
        assertEquals(-250, centeringOffset(bandHeight = band, itemHeight = 500))
    }

    @Test
    fun `a message too tall to centre is aligned to the band's start`() {
        assertEquals(0, centeringOffset(bandHeight = band, itemHeight = 2000))
    }

    @Test
    fun `a message exactly as tall as the band is aligned to it too`() {
        assertEquals(0, centeringOffset(bandHeight = band, itemHeight = band))
    }

    @Test
    fun `a height that could not be measured puts the leading edge on the middle`() {
        // centerItem falls back to 0 when the target never reported a size. Half a message off is
        // the worst it can then be, and it is off toward the top, where there is transcript to see.
        assertEquals(-500, centeringOffset(bandHeight = band, itemHeight = 0))
    }
}
