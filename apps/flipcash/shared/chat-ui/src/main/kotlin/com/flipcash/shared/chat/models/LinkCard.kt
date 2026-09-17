package com.flipcash.shared.chat.models

/**
 * A link in a message that renders as a card above the sender's text.
 *
 * Display-ready by construction: whoever builds it has already classified the URL and formatted
 * the amount, so `chat-ui` needs neither the router nor a currency formatter. See
 * `LinkCardClassifier` and `LinkCardResolver` in `:apps:flipcash:features:messenger`.
 */
sealed interface LinkCard {

    /** The URL the card stands for, jump wrapper already unwrapped. Tapping the card opens this. */
    val url: String

    data class Cash(
        override val url: String,
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
                val tokenSymbol: String,
                val iconUrl: String?,
                val issuedByViewer: Boolean,
            ) : State
        }

        enum class Claim { Claimable, Claimed, Expired }
    }
}
