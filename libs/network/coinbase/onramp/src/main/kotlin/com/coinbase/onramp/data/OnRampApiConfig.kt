package com.coinbase.onramp.data

data class OnRampApiConfig(
    val scheme: String,
    val host: String,
    val path: String,
    val method: String,
    val useSandbox: Boolean = false,
) {
    val baseUrl: String
        get() = "$scheme://$host"
    val url: String
        get() = "$scheme://$host$path"
}
