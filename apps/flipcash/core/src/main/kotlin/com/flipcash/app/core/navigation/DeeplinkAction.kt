package com.flipcash.app.core.navigation

import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint

sealed interface DeeplinkAction {
    data class Navigate(val routes: List<com.flipcash.app.core.AppRoute>) : DeeplinkAction
    data class Login(val entropy: String) : DeeplinkAction
    data class OpenCashLink(val entropy: String) : DeeplinkAction
    data class PresentTipCard(val userId: ID): DeeplinkAction

    /**
     * A `/token/{mint}` link.
     *
     * v2 opens it as the wallet's *expanded card* — the same overlay a tap on the card produces,
     * with the same chrome (✕, no back chevron) and the same dismissal. Pushing it instead gives a
     * screen that belongs to a stack the user never navigated. Mirrors iOS `DeepLinkController`,
     * which sets `router.requestedCardMint` rather than pushing `.currencyInfo`.
     *
     * v1 has no card expansion, so [routes] carries the equivalent presentation there (the wallet
     * sheet with token info inside it).
     */
    data class OpenToken(
        val mint: Mint,
        val routes: List<com.flipcash.app.core.AppRoute>,
    ) : DeeplinkAction

    data object None : DeeplinkAction
}
