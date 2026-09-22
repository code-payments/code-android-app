package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How many of the selection bar's actions stay as icons before the rest collapse into the overflow.
 *
 * The number a phone lands on is the whole point of the budget, so these pin it at real device
 * widths rather than at the fraction. Three is the figure that matters: another participant's text
 * message offers reply, copy and report, and that is the common case.
 */
class MessageActionCapacityTest {

    /**
     * The budget is measured on the slot the actions are laid out in, not on the screen. The app
     * bar wraps that slot in 5dp either side, so the slot is 10dp short of the device — the gap
     * that put a 400dp phone one capacity below three.
     */
    private fun capacityOnDevice(deviceWidth: Int) = inlineActionCapacity(
        // What the bar actually lays out: a 40dp circular button with 10dp between.
        barWidth = (deviceWidth - 10).dp,
        buttonSize = 40.dp,
        spacing = 10.dp,
    )

    @Test
    fun `a 400dp phone fits all three of another participant's actions`() {
        // A real device width, and the one this was reported on: 0.35 of its 390dp slot came to
        // 136.5dp against the 140dp three buttons and their two gaps need, so copy and report sat
        // in a menu with most of the bar empty.
        assertTrue(capacityOnDevice(400) >= 3)
    }

    @Test
    fun `the narrowest phone worth supporting still fits all three`() {
        // 320dp is the floor, and the binding case: it is what the fraction has to clear.
        assertEquals(3, capacityOnDevice(320))
    }

    @Test
    fun `a typical phone fits all three`() {
        assertTrue(capacityOnDevice(411) >= 3)
    }

    @Test
    fun `a tablet fits every action the bar has`() {
        // Five is the most the bar ever offers — reply, delete, copy, edit and report on your own
        // text. The budget is a ceiling rather than a width, so a roomier screen leaving it unspent
        // costs nothing; what matters is only that nothing is forced into a menu.
        assertTrue(capacityOnDevice(800) >= 5)
    }

    @Test
    fun `a bar with no room to speak of still offers one action`() {
        // Below one button's width the budget goes negative; the bar shows an icon and a menu
        // rather than a menu alone, which would hide reply behind two taps on every message.
        assertEquals(1, inlineActionCapacity(barWidth = 0.dp, buttonSize = 40.dp, spacing = 10.dp))
    }
}
