package com.flipcash.app.updates

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionalUpdateTest {

    private fun update(priority: Int = 0, stalenessDays: Int = 0) =
        OptionalUpdate(versionCode = 100, priority = priority, stalenessDays = stalenessDays)

    @Test
    fun `priority at the threshold is eligible on its own`() {
        assertTrue(update(priority = 4).isToastEligible(lastShownVersionCode = null))
    }

    @Test
    fun `a week behind is eligible on its own`() {
        assertTrue(update(stalenessDays = 7).isToastEligible(lastShownVersionCode = null))
    }

    @Test
    fun `a low-priority update Play learned of recently is not eligible`() {
        assertFalse(update(priority = 3, stalenessDays = 6).isToastEligible(lastShownVersionCode = null))
    }

    @Test
    fun `the toast never shows twice for one version`() {
        assertFalse(update(priority = 5).isToastEligible(lastShownVersionCode = 100))
    }

    @Test
    fun `a newer version shows again after an older one was seen`() {
        assertTrue(update(priority = 5).isToastEligible(lastShownVersionCode = 99))
    }
}
