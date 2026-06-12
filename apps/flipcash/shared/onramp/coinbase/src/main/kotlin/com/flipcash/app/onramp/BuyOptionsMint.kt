package com.flipcash.app.onramp

@JvmInline
value class BuyOptionsMint(val symbol: String) {
    companion object {
        val USDF = BuyOptionsMint("USDF")
    }
}
