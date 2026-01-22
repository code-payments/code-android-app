package com.getcode.opencode.internal.solana.model

import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable


@Serializable(with = PublicKeyAsStringSerializer::class)
class LiquidityPool(
    val address: PublicKey,
    val authority: PublicKey,
    val usdfMint: Mint,
    val otherMint: Mint,
    val usdfVault: Vault,
    val otherVault: Vault,
    val bump: Int,
    val usdfVaultBump: Int,
    val otherVaultBump: Int,
    val usdfDecimals: Int,
    val otherDecimals: Int,
) {
    companion object {
        val usdf: LiquidityPool
            get() = LiquidityPool(
                address = PublicKey("8q2Kv6wMKDhkg92itiYGxr6jvSHvUhuCay6zrhUncyvK"),
                authority = vmAuthority,
                usdfMint = Mint.usdf,
                otherMint = Mint.usdc,
                usdfVault = Vault.usdf,
                otherVault = Vault.usdc,
                bump = 255,
                usdfVaultBump = 255,
                otherVaultBump = 255,
                usdfDecimals = 6,
                otherDecimals = 6
            )
    }
}