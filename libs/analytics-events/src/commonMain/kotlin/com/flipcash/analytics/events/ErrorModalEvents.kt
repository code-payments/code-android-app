package com.flipcash.analytics.events

import com.flipcash.analytics.event

object ErrorModalEvents {
    // DRIFT: iOS's Screen is `presentedSheet.description` or "scan" where Android's is the
    // back stack top's `screenName()`, and iOS never sends Call Site.
    fun displayed(title: String, message: String, screen: String?, callSite: String?) =
        event("Error Modal Displayed") {
            text("Title", title)
            text("Message", message)
            text("Screen", screen)
            text("Call Site", callSite)
        }
}
