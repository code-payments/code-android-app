package com.flipcash.shared.chat.models

import com.getcode.opencode.model.financial.Token

/**
 * A link in a message that renders as a card in place of the link text.
 *
 * Display-ready by construction: whoever builds it has already classified the URL and formatted
 * the amount, so `chat-ui` needs neither the router nor a currency formatter. See
 * `LinkCardClassifier` and `LinkCardResolver` in `:apps:flipcash:features:messenger`.
 */
sealed interface LinkCard {

    /** The URL the card stands for, jump wrapper already unwrapped. Tapping the card opens this. */
    val url: String

    /**
     * Where the link sits in the message text, as a half-open UTF-16 range — the span the bubble
     * would otherwise underline. The card replaces it, so the bubble needs to know which
     * characters to drop; matching on [url] instead would miss a jump-wrapped link, whose text is
     * the wrapper and whose [url] is the target.
     */
    val start: Int
    val end: Int

    data class Cash(
        override val url: String,
        override val start: Int,
        override val end: Int,
        val entropy: String,
        val state: State,
    ) : LinkCard {

        sealed interface State {
            /**
             * Branded, no network. Also what a failed, timed-out, offline or switched-off
             * resolution renders as — a cash link card never shows an error, because the
             * link is still perfectly openable and the card is decoration over it.
             */
            data object Unresolved : State

            data class Resolved(
                /** Pre-formatted, e.g. "$15.00". */
                val amount: String,
                val claim: Claim,
                /**
                 * The mint the link pays out, carried whole rather than reduced to a name and an
                 * icon: the card is painted as the token's own bill, and the gradient comes from
                 * [Token.billCustomizations]. A link whose mint has no metadata never resolves.
                 */
                val token: Token,
                val issuedByViewer: Boolean,
            ) : State
        }

        enum class Claim { Claimable, Claimed, Expired }
    }
}
