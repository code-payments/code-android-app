package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkCardResolverTest {

    private val card = LinkCard.Cash(
        url = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3",
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Unresolved,
    )

    @Test
    fun `a failed lookup stays unresolved rather than erroring`() = runTest {
        val resolver = LinkCardResolver(lookup = { Result.failure(IllegalStateException("offline")) })
        assertEquals(LinkCard.Cash.State.Unresolved, (resolver.resolve(card) as LinkCard.Cash).state)
    }

    @Test
    fun `a successful lookup fills the card in`() = runTest {
        val resolver = LinkCardResolver(lookup = {
            Result.success(
                LinkCardResolver.Snapshot(
                    amount = "$15.00",
                    claim = LinkCard.Cash.Claim.Claimable,
                    tokenSymbol = "Cash",
                    iconUrl = null,
                    issuedByViewer = false,
                )
            )
        })
        val state = (resolver.resolve(card) as LinkCard.Cash).state
        assertTrue(state is LinkCard.Cash.State.Resolved)
        assertEquals("$15.00", state.amount)
    }

    @Test
    fun `the same entropy is only looked up once`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(lookup = {
            calls++
            Result.failure(IllegalStateException("offline"))
        })
        resolver.resolve(card)
        resolver.resolve(card)
        assertEquals(1, calls)
    }
}
