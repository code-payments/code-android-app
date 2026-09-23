package com.flipcash.analytics.events

import com.flipcash.analytics.event

/**
 * [url] arrives with its query and fragment already stripped. `type` is a string because the
 * two apps' deeplink vocabularies differ.
 */
object DeeplinkEvents {
    fun open(url: String) = event("Deeplink: Open") {
        text("URL", url)
    }

    // DRIFT: iOS's Type vocabulary is {Login, CashLink, EmailVerification, TokenInfo, Chat,
    // ChatSendCash, Tip, Username, Wallet, DiscoverCurrencies, "Sheet:<x>"}, and iOS sends this
    // for every link where Android sends it only on the gallery QR path.
    fun parsed(type: String) = event("Deeplink: Parse") {
        text("Type", type)
    }

    fun parseFailed(url: String) = event("Deeplink: Parse") {
        text("Error", "Failed to parse deeplink => $url")
    }

    // DRIFT: iOS never passes Error, and sends this for every kind where Android sends it only
    // for a cash link.
    fun routed(type: String, error: String?) = event("Deeplink: Routed") {
        text("Type", type)
        text("Error", error)
    }
}
