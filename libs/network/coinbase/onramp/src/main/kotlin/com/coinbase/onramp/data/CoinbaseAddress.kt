package com.coinbase.onramp.data

import kotlinx.serialization.Serializable

@Serializable
data class CoinbaseAddress(
    val address: String,
    val blockchain: String,
)
