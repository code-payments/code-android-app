package com.flipcash.app.messenger.internal.screens.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The jump centres the message it lands on, and it does that in two scrolls: one that parks the
 * target's leading edge mid-band and measures it, and one that corrects by where it actually came
 * to rest. This is the correction — the only part of the landing that can be checked without a
 * laid-out list.
 *
 * The band is what is left of the viewport once content padding is off it, in the coordinates
 * `LazyListItemInfo.offset` is reported in.
 */
class JumpCenteringTest {

    private val band = 100..1100 // a 1000px band starting 100px in

    @Test
    fun `an item already centred needs no correction`() {
        // Spans 500..700, so its centre is at 600 — the middle of the band.
        assertEquals(0f, centeringDelta(itemOffset = 500, itemHeight = 200, band = band))
    }

    @Test
    fun `an item short of the middle is scrolled on`() {
        // The first pass parks the leading edge at the middle, so this is the usual case: the item
        // sits half its own height past centre and comes back by exactly that.
        assertEquals(100f, centeringDelta(itemOffset = 600, itemHeight = 200, band = band))
    }

    @Test
    fun `an item past the middle is scrolled back`() {
        assertEquals(-150f, centeringDelta(itemOffset = 350, itemHeight = 200, band = band))
    }

    @Test
    fun `an item too tall to centre is aligned to the band's start`() {
        assertEquals(400f, centeringDelta(itemOffset = 500, itemHeight = 2000, band = band))
    }

    @Test
    fun `an item exactly as tall as the band is aligned to it too`() {
        assertEquals(400f, centeringDelta(itemOffset = 500, itemHeight = 1000, band = band))
    }
}
