package com.flipcash.app.core.tokens

import android.os.Parcelable
import com.getcode.solana.keys.Mint
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Where a currency-info screen was opened from. Each origin states how the screen is presented
 * and what it does once a buy of its currency succeeds; [afterSwap] turns that into the step the
 * screen takes when a swap it launched returns.
 */
@Serializable
@Parcelize
sealed interface TokenInfoEntry : Parcelable {
    val presentation: TokenInfoPresentation
    val afterBuy: AfterBuy

    /** The wallet, or any route that lands on a currency without a more specific origin. */
    @Serializable
    data object Wallet : TokenInfoEntry {
        override val presentation get() = TokenInfoPresentation.CardExpand
        override val afterBuy get() = AfterBuy.Stay
    }

    /** A token deeplink, opened or scanned. */
    @Serializable
    data object Deeplink : TokenInfoEntry {
        override val presentation get() = TokenInfoPresentation.CardExpand
        override val afterBuy get() = AfterBuy.Stay
    }

    /** Token discovery's list. */
    @Serializable
    data object Discovery : TokenInfoEntry {
        override val presentation get() = TokenInfoPresentation.Push
        override val afterBuy get() = AfterBuy.Stay
    }

    /** A currency mentioned in a chat transcript. */
    @Serializable
    data object Chat : TokenInfoEntry {
        override val presentation get() = TokenInfoPresentation.Push
        override val afterBuy get() = AfterBuy.Stay
    }

    /**
     * A group chat's buy-in gate. The reader came to buy their way into the group, so the
     * purchase finishing is the moment to put them back in front of Join.
     */
    @Serializable
    data object ChatGate : TokenInfoEntry {
        override val presentation get() = TokenInfoPresentation.Push
        override val afterBuy get() = AfterBuy.ReturnToOpener
    }

    companion object {
        /** Every origin. Covered by a test against the serializer's subclass list. */
        val all: List<TokenInfoEntry> = listOf(Wallet, Deeplink, Discovery, Chat, ChatGate)
    }
}

enum class TokenInfoPresentation {
    /** The wallet card grows into the screen in place; the screen leads with ✕. */
    CardExpand,

    /** An ordinary stack push: slides in, leads with a back arrow. */
    Push,
}

enum class AfterBuy {
    /** Stay on the currency's info screen. */
    Stay,

    /** Pop back to whatever opened the screen. */
    ReturnToOpener,
}

/** The step a currency-info screen takes when a swap it launched returns. */
enum class TokenInfoSwapOutcome {
    Stay,
    Pop,
    OpenDeposit,
}

/**
 * The step to take when a swap launched from the info screen for [mint] returns [result].
 *
 * [afterBuy] applies only to a buy of [mint] itself. Add Money from this screen also ends in a
 * successful buy, but of USDF to fund this one, and the reader still has this purchase to make.
 */
fun TokenInfoEntry.afterSwap(mint: Mint, purpose: SwapPurpose, result: SwapResult): TokenInfoSwapOutcome =
    when (result) {
        SwapResult.OpenDeposit -> TokenInfoSwapOutcome.OpenDeposit
        SwapResult.Canceled -> TokenInfoSwapOutcome.Stay
        is SwapResult.Success -> {
            val boughtThisCurrency = purpose is SwapPurpose.Buy && purpose.mint == mint
            when {
                !boughtThisCurrency -> TokenInfoSwapOutcome.Stay
                afterBuy == AfterBuy.ReturnToOpener -> TokenInfoSwapOutcome.Pop
                else -> TokenInfoSwapOutcome.Stay
            }
        }
    }
