package com.getcode.network.jwt

data class JwtSecuredEndpoint(
    val provider: ApiProvider,
    val host: String,
    val path: String,
    val method: String,
)

sealed interface ApiProvider {
    data object Coinbase: ApiProvider
}

