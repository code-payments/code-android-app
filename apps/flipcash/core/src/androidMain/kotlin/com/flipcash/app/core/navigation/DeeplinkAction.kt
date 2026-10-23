package com.flipcash.app.core.navigation

import com.flipcash.app.core.tipping.TipCardOwner
import com.getcode.solana.keys.Mint

sealed interface DeeplinkAction {
    data class Navigate(val routes: List<com.flipcash.app.core.AppRoute>) : DeeplinkAction
    data class Login(val entropy: String) : DeeplinkAction
    data class OpenCashLink(val entropy: String) : DeeplinkAction

    /**
     * Present someone's tip card. [owner] carries how the link named them — `flipcash.com/{id}`
     * (or the older `/tip/{id}`) by id, `flipcash.com/{username}` by handle — because resolving
     * the handle is a server round trip, and that belongs to the session rather than to the
     * router.
     */
    data class PresentTipCard(val owner: TipCardOwner): DeeplinkAction

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

    /**
     * A link the app captured but doesn't route — hand it back to the web.
     *
     * Only the bare `flipcash.com` host produces one. Its path space is shared with the website
     * (/download, /privacy, /terms), and the App Link filter can only narrow it to the handle
     * charset — which those words also satisfy, and which older platforms ignore entirely. Rather
     * than dead-end the tap on the home screen, the URL goes to a browser.
     */
    data class OpenExternally(val url: String) : DeeplinkAction

    data object None : DeeplinkAction
}
