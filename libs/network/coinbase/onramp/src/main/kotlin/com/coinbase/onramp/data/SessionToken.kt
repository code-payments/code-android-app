package com.coinbase.onramp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionTokenRequest(
    val addresses: List<CoinbaseAddress>
)

@Serializable
data class SessionTokenResponse(
    val token: String,
    @SerialName("channel_id")
    val channelId: String,
)