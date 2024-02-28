package com.getcode.solana.keys

import org.kin.sdk.base.tools.Base58

typealias Mint = PublicKey

object TokenAddressProvider {
    private val addresses = mapOf(
        "kin" to "kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6",
        "usdc" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    )

    fun getAddress(key: String): Mint? {
        val address = addresses[key] ?: return null
        return try {
            Mint(Base58.decode(address).toList())
        } catch (e: IllegalArgumentException) {
            // Log error or handle exception
            null
        }
    }
}

val Mint.kin: Mint?
    get() = TokenAddressProvider.getAddress("kin")

val Mint.usdc: Mint?
    get() = TokenAddressProvider.getAddress("usdc")
