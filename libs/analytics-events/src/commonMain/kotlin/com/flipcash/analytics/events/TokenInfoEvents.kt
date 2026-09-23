package com.flipcash.analytics.events

import com.flipcash.analytics.TokenInfoSource
import com.flipcash.analytics.event

object TokenInfoEvents {
    fun opened(source: TokenInfoSource, mint: String) = event("Token Info: Opened From ${source.value}") {
        text("Mint", mint)
    }
}
