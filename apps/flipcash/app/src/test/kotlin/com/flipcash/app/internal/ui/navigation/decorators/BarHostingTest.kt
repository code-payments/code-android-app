package com.flipcash.app.internal.ui.navigation.decorators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which entry puts the app's bars on screen.
 *
 * The rule is easy to get wrong in the direction that costs a whole feature: get it wrong for a
 * sheet and a bar raised from inside that sheet is never seen, because it renders behind the sheet
 * or is clipped to it. Report asks for a confirmation before it closes, so it needs the answer to
 * be yes while it is still open.
 */
class BarHostingTest {

    @Test
    fun `a route hosts its own bars`() {
        assertTrue(hostsBars(isTopEntry = true, isSheet = false, coversScreen = false))
    }

    @Test
    fun `a sheet that covers the screen hosts its own bars`() {
        assertTrue(hostsBars(isTopEntry = true, isSheet = true, coversScreen = true))
    }

    @Test
    fun `a partial sheet leaves bars to the route beneath it`() {
        assertFalse(hostsBars(isTopEntry = true, isSheet = true, coversScreen = false))
    }

    @Test
    fun `an entry that is not on top never hosts bars`() {
        assertFalse(hostsBars(isTopEntry = false, isSheet = false, coversScreen = false))
        assertFalse(hostsBars(isTopEntry = false, isSheet = true, coversScreen = true))
    }
}
