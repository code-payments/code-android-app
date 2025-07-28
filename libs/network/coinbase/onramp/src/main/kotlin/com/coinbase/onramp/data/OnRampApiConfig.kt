package com.coinbase.onramp.data

data class OnRampApiConfig(
    val schema: String,
    val host: String,
    val path: String,
    val method: String,
    val useSandbox: Boolean = false,
) {
    val baseUrl: String
        get() = "$schema://$host"
    val url: String
        get() = "$schema://$host$path"
}
