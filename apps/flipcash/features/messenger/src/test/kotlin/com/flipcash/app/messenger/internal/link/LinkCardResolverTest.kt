package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.financial.Token
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkCardResolverTest {

    private val card = LinkCard.Cash(
        url = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3",
        start = 0,
        end = 54,
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Unresolved,
    )

    private fun snapshot() = LinkCardResolver.Snapshot(
        amount = "$15.00",
        claim = LinkCard.Cash.Claim.Claimable,
        token = mock<Token>(),
        issuedByViewer = false,
    )

    @Test
    fun `a failed lookup stays unresolved rather than erroring`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            lookup = { Result.failure(IllegalStateException("offline")) },
        )
        assertEquals(LinkCard.Cash.State.Unresolved, (resolver.resolve(card) as LinkCard.Cash).state)
    }

    @Test
    fun `a successful lookup fills the card in`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            lookup = { Result.success(snapshot()) },
        )
        val state = (resolver.resolve(card) as LinkCard.Cash).state
        assertTrue(state is LinkCard.Cash.State.Resolved)
        assertEquals("$15.00", state.amount)
    }

    @Test
    fun `a resolved link is only looked up once`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            lookup = {
                calls++
                Result.success(snapshot())
            },
        )
        resolver.resolve(card)
        resolver.resolve(card)
        assertEquals(1, calls)
    }

    @Test
    fun `a failed lookup is asked again rather than held`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            lookup = {
                calls++
                Result.failure(IllegalStateException("offline"))
            },
        )
        resolver.resolve(card)
        resolver.resolve(card)
        // The transcript re-maps constantly; one cancelled or offline moment cannot be what
        // decides the card until the reader leaves the chat.
        assertEquals(2, calls)
    }
}
