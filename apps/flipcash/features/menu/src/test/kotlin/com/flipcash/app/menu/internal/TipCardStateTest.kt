package com.flipcash.app.menu.internal

import com.flipcash.app.core.bill.Scannable
import com.flipcash.services.models.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The "You" tab used to model the tip card as a nullable, which conflated "still resolving" with
 * "this account has no display name". The header drew nothing for either, so a nameless account got
 * a tab with no card, no prompt and no way to claim one. The three states must stay distinct — and
 * only a claimed card is shareable, linkable or expandable.
 */
class TipCardStateTest {

    private val aCard = Scannable.TipCard(data = emptyList(), user = UserProfile.Empty)

    @Test
    fun `nothing is shareable while the card is still resolving`() {
        val state = MenuScreenViewModel.State()
        assertEquals(MenuScreenViewModel.TipCardState.Unknown, state.tipCardState)
        assertNull(state.tipCard)
        assertNull(state.tipLink)
    }

    @Test
    fun `an unclaimed stand-in is never treated as the viewer's card`() {
        val state = MenuScreenViewModel.State(
            tipCardState = MenuScreenViewModel.TipCardState.Unclaimed(placeholder = aCard),
        )
        // Drawn blurred behind the claim prompt, but it is not a card the viewer owns: no share,
        // no download, no link, and the caller's `canExpand` gate stays closed.
        assertNull(state.tipCard)
        assertNull(state.tipLink)
    }

    @Test
    fun `a claimed card carries its shareable link`() {
        val state = MenuScreenViewModel.State(
            tipCardState = MenuScreenViewModel.TipCardState.Claimed(aCard, "https://flipcash.com/x"),
        )
        assertEquals(aCard, state.tipCard)
        assertEquals("https://flipcash.com/x", state.tipLink)
    }
}
