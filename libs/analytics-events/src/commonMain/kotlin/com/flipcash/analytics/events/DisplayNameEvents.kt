package com.flipcash.analytics.events

import com.flipcash.analytics.DisplayNameSource
import com.flipcash.analytics.event

object DisplayNameEvents {
    /** The user had no display name before this submission. */
    fun set(source: DisplayNameSource) = event("Display Name Set") {
        text("Source", source.value)
    }

    /** The user replaced an existing display name. */
    fun updated(source: DisplayNameSource) = event("Display Name Updated") {
        text("Source", source.value)
    }
}
