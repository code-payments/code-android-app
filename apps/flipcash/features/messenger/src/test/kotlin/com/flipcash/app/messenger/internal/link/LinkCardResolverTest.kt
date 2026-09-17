package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkCardResolverTest {

    private companion object {
        const val MINT = "So11111111111111111111111111111111111111112"
    }

    private val card = LinkCard.Cash(
        url = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3",
        start = 0,
        end = 54,
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Unresolved,
    )

    private val tokenCard = LinkCard.TokenInfo(
        url = "https://app.flipcash.com/token/$MINT",
        start = 0,
        end = 74,
        mint = Mint(MINT),
        state = LinkCard.TokenInfo.State.Unresolved,
    )

    private fun snapshot() = LinkCardResolver.Snapshot(
        amount = "$15.00",
        claim = LinkCard.Cash.Claim.Claimable,
        token = mock<Token>(),
    )

    @Test
    fun `a failed lookup stays unresolved rather than erroring`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.failure(IllegalStateException("offline")) },
            tokenMetadata = { Result.failure(IllegalStateException("offline")) },
        )
        assertEquals(LinkCard.Cash.State.Unresolved, (resolver.resolve(card) as LinkCard.Cash).state)
    }

    @Test
    fun `a successful lookup fills the card in`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.success(snapshot()) },
            tokenMetadata = { Result.success(mock<Token>()) },
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
            giftCard = {
                calls++
                Result.success(snapshot())
            },
            tokenMetadata = { Result.success(mock<Token>()) },
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
            giftCard = {
                calls++
                Result.failure(IllegalStateException("offline"))
            },
            tokenMetadata = { Result.failure(IllegalStateException("offline")) },
        )
        resolver.resolve(card)
        resolver.resolve(card)
        // The transcript re-maps constantly; one cancelled or offline moment cannot be what
        // decides the card until the reader leaves the chat.
        assertEquals(2, calls)
    }

    @Test
    fun `a token link resolves to the mint's metadata`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.failure(IllegalStateException("not asked")) },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        val resolved = resolver.resolve(tokenCard) as LinkCard.TokenInfo
        assertTrue(resolved.state is LinkCard.TokenInfo.State.Resolved)
    }

    @Test
    fun `an unknown mint stays unresolved rather than erroring`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.failure(IllegalStateException("not asked")) },
            tokenMetadata = { Result.failure(IllegalStateException("no metadata")) },
        )
        val resolved = resolver.resolve(tokenCard) as LinkCard.TokenInfo
        assertEquals(LinkCard.TokenInfo.State.Unresolved, resolved.state)
    }

    @Test
    fun `an invalidated cash link is asked again and reports the new claim state`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = {
                calls++
                Result.success(
                    snapshot().copy(
                        claim = if (calls == 1) {
                            LinkCard.Cash.Claim.Claimable
                        } else {
                            LinkCard.Cash.Claim.Claimed
                        },
                    ),
                )
            },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        val before = (resolver.resolve(card) as LinkCard.Cash).state
        assertEquals(LinkCard.Cash.Claim.Claimable, (before as LinkCard.Cash.State.Resolved).claim)

        resolver.invalidateCash(card.entropy)

        val after = (resolver.resolve(card) as LinkCard.Cash).state
        assertEquals(LinkCard.Cash.Claim.Claimed, (after as LinkCard.Cash.State.Resolved).claim)
        assertEquals(2, calls)
    }

    @Test
    fun `invalidating one cash link leaves the others held`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { calls++; Result.success(snapshot()) },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        val other = card.copy(entropy = "8mXeQ2vTb4pLzRw9dKcHfA")
        resolver.resolve(card)
        resolver.resolve(other)
        assertEquals(2, calls)

        // The claim signal names one entropy, so it must not cost every other card its answer.
        resolver.invalidateCash(card.entropy)
        resolver.resolve(other)
        assertEquals(2, calls)
    }

    @Test
    fun `refreshing drops a claimable card and keeps a claimed one`() = runTest {
        val claims = mutableMapOf(
            card.entropy to LinkCard.Cash.Claim.Claimable,
            "8mXeQ2vTb4pLzRw9dKcHfA" to LinkCard.Cash.Claim.Claimed,
        )
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { entropy ->
                calls++
                Result.success(snapshot().copy(claim = claims.getValue(entropy)))
            },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        val claimed = card.copy(entropy = "8mXeQ2vTb4pLzRw9dKcHfA")
        resolver.resolve(card)
        resolver.resolve(claimed)
        assertEquals(2, calls)

        assertTrue(resolver.refreshClaimable())

        // Only the claimable one is asked again: a claimed link does not become claimable, so
        // holding it is the whole reason this is cheap enough to run on a timer.
        claims[card.entropy] = LinkCard.Cash.Claim.Claimed
        val after = (resolver.resolve(card) as LinkCard.Cash).state
        resolver.resolve(claimed)
        assertEquals(3, calls)
        assertEquals(LinkCard.Cash.Claim.Claimed, (after as LinkCard.Cash.State.Resolved).claim)
    }

    @Test
    fun `refreshing an expired card keeps it`() = runTest {
        var calls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = {
                calls++
                Result.success(snapshot().copy(claim = LinkCard.Cash.Claim.Expired))
            },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        resolver.resolve(card)

        assertFalse(resolver.refreshClaimable())

        resolver.resolve(card)
        assertEquals(1, calls)
    }

    @Test
    fun `refreshing a transcript with no claimable card reports nothing to do`() = runTest {
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { Result.failure(IllegalStateException("not asked")) },
            tokenMetadata = { Result.success(mock<Token>()) },
        )
        resolver.resolve(tokenCard)

        // What makes the tick free for nearly every chat: nothing dropped, so the caller has no
        // reason to re-map and nothing is queried.
        assertFalse(resolver.refreshClaimable())
    }

    @Test
    fun `a cash link and a token link do not share a query`() = runTest {
        var cashCalls = 0
        var tokenCalls = 0
        val resolver = LinkCardResolver(
            scope = backgroundScope,
            giftCard = { cashCalls++; Result.success(snapshot()) },
            tokenMetadata = { tokenCalls++; Result.success(mock<Token>()) },
        )
        resolver.resolve(card)
        resolver.resolve(tokenCard)
        assertEquals(1, cashCalls)
        assertEquals(1, tokenCalls)
    }
}
