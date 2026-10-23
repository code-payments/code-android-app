package com.flipcash.app.core.tokens

import com.getcode.solana.keys.Mint
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenInfoEntryTest {

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    private val buyThis = SwapPurpose.Buy(mint)
    private val success = SwapResult.Success()

    @Test
    fun `all lists every origin`() {
        // AppContent picks the transition from TokenInfoEntry.all, so an origin missing from it
        // would silently get the card-expand transition.
        val subclasses = TokenInfoEntry.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        assertEquals(subclasses, TokenInfoEntry.all.map { it::class.qualifiedName }.toSet())
    }

    @Test
    fun `wallet and deeplink expand the card, list and chat origins push`() {
        assertEquals(
            mapOf(
                TokenInfoEntry.Wallet to TokenInfoPresentation.CardExpand,
                TokenInfoEntry.Deeplink to TokenInfoPresentation.CardExpand,
                TokenInfoEntry.Discovery to TokenInfoPresentation.Push,
                TokenInfoEntry.Chat to TokenInfoPresentation.Push,
                TokenInfoEntry.ChatGate to TokenInfoPresentation.Push,
            ),
            TokenInfoEntry.all.associateWith { it.presentation },
        )
    }

    @Test
    fun `buying this currency returns to the opener only from a chat gate`() {
        assertEquals(
            mapOf(
                TokenInfoEntry.Wallet to TokenInfoSwapOutcome.Stay,
                TokenInfoEntry.Deeplink to TokenInfoSwapOutcome.Stay,
                TokenInfoEntry.Discovery to TokenInfoSwapOutcome.Stay,
                TokenInfoEntry.Chat to TokenInfoSwapOutcome.Stay,
                TokenInfoEntry.ChatGate to TokenInfoSwapOutcome.Pop,
            ),
            TokenInfoEntry.all.associateWith { it.afterSwap(mint, buyThis, success) },
        )
    }

    @Test
    fun `adding money from a chat gate stays, since this currency is still unbought`() {
        assertEquals(
            TokenInfoSwapOutcome.Stay,
            TokenInfoEntry.ChatGate.afterSwap(mint, SwapPurpose.Buy(Mint.usdf), success),
        )
    }

    @Test
    fun `selling or converting from a chat gate stays`() {
        assertEquals(TokenInfoSwapOutcome.Stay, TokenInfoEntry.ChatGate.afterSwap(mint, SwapPurpose.Sell(mint), success))
        assertEquals(
            TokenInfoSwapOutcome.Stay,
            TokenInfoEntry.ChatGate.afterSwap(mint, SwapPurpose.Convert(mint, Mint.usdf), success),
        )
    }

    @Test
    fun `a canceled buy stays`() {
        TokenInfoEntry.all.forEach {
            assertEquals(TokenInfoSwapOutcome.Stay, it.afterSwap(mint, buyThis, SwapResult.Canceled))
        }
    }

    @Test
    fun `open deposit opens deposit from every origin`() {
        TokenInfoEntry.all.forEach {
            assertEquals(TokenInfoSwapOutcome.OpenDeposit, it.afterSwap(mint, buyThis, SwapResult.OpenDeposit))
        }
    }
}
