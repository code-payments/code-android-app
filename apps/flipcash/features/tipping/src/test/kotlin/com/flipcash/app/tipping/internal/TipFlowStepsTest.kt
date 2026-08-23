package com.flipcash.app.tipping.internal

import com.flipcash.app.core.tipping.TipStep
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tipping flow doubles as the v2 "Chats" root tab. A root tab must not be swapped out for the
 * setup screen when the account has no display name: the list — and so its empty state — is the tab,
 * and the setup screen's Close has no sheet to dismiss there.
 */
class TipFlowStepsTest {

    @Test
    fun `chats tab opens on the list even without a display name`() {
        assertEquals(
            listOf(TipStep.Tips),
            TipFlowViewModel.stepsFor(hasProfile = false, resumed = false, isNewUi = true),
        )
    }

    @Test
    fun `chats tab opens on the list for a named account`() {
        assertEquals(
            listOf(TipStep.Tips),
            TipFlowViewModel.stepsFor(hasProfile = true, resumed = false, isNewUi = true),
        )
    }

    /** The resumed handoff is a v1 sheet re-entry; the v2 tip card has its own tab. */
    @Test
    fun `chats tab ignores the post-setup handoff`() {
        assertEquals(
            listOf(TipStep.Tips),
            TipFlowViewModel.stepsFor(hasProfile = true, resumed = true, isNewUi = true),
        )
    }

    @Test
    fun `v1 sheet still starts setup for a nameless account`() {
        assertEquals(
            listOf(TipStep.Intro),
            TipFlowViewModel.stepsFor(hasProfile = false, resumed = false, isNewUi = false),
        )
    }

    @Test
    fun `v1 sheet lands on the tip card after setup`() {
        assertEquals(
            listOf(TipStep.TipCard),
            TipFlowViewModel.stepsFor(hasProfile = false, resumed = true, isNewUi = false),
        )
    }

    @Test
    fun `v1 sheet opens on the tips list once set up`() {
        assertEquals(
            listOf(TipStep.Tips),
            TipFlowViewModel.stepsFor(hasProfile = true, resumed = false, isNewUi = false),
        )
    }
}
