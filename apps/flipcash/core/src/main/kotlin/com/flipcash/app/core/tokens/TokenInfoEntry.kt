package com.flipcash.app.core.tokens

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Where a currency-info screen was opened from. The origin decides how the screen is presented
 * and what a successful buy of its currency does.
 */
@Serializable
@Parcelize
sealed interface TokenInfoEntry : Parcelable {
    /**
     * A normal stack push (slide in, back arrow) rather than the wallet card-expand presentation
     * (fade-in-place, ✕ dismiss). True when the screen is drilled into from a list or transcript,
     * where there is no card for it to grow from.
     */
    val asPush: Boolean get() = false

    /** Pop back to whatever opened this screen once a buy of its currency succeeds. */
    val returnAfterBuy: Boolean get() = false

    /** The wallet, or any route that lands on a currency without a more specific origin. */
    @Serializable
    data object Wallet : TokenInfoEntry

    /** A token deeplink, opened or scanned. */
    @Serializable
    data object Deeplink : TokenInfoEntry

    /** Token discovery's list. */
    @Serializable
    data object Discovery : TokenInfoEntry {
        override val asPush: Boolean get() = true
    }

    /** A currency mentioned in a chat transcript. */
    @Serializable
    data object Chat : TokenInfoEntry {
        override val asPush: Boolean get() = true
    }

    /**
     * A group chat's buy-in gate. The reader came to buy their way into the group, so the
     * purchase finishing is the moment to put them back in front of Join.
     */
    @Serializable
    data object ChatGate : TokenInfoEntry {
        override val asPush: Boolean get() = true
        override val returnAfterBuy: Boolean get() = true
    }
}
