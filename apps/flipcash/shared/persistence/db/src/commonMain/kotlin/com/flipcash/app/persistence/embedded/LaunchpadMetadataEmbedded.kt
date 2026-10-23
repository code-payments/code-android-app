package com.flipcash.app.persistence.embedded

import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey


data class LaunchpadMetadataEmbedded(
    @ColumnInfo(name = "currency_config")
    val currencyConfig: String,

    @ColumnInfo(name = "liquidity_pool")
    val liquidityPool: String,

    @ColumnInfo(name = "seed")
    val seed: String,

    @ColumnInfo(name = "authority")
    val authority: String,

    @ColumnInfo(name = "mint_vault")
    val mintVault: String,

    @ColumnInfo(name = "core_mint_vault")
    val coreMintVault: String,

    @ColumnInfo(name = "circulating_supply_quarks")
    val currentCirculatingSupplyQuarks: Long,

    @ColumnInfo(name = "sell_fee_bps")
    val sellFeeBps: Int,

    @ColumnInfo(name = "price_amount_usd")
    val priceAmount: Double,

    @ColumnInfo(name = "market_cap_amount_usd")
    val marketCapAmount: Double,
) {
    @get:Ignore
    val currencyConfigKey: PublicKey
        get() = PublicKey(currencyConfig)

    @get:Ignore
    val liquidityPoolKey: PublicKey
        get() = PublicKey(liquidityPool)

    @get:Ignore
    val seedKey: PublicKey
        get() = PublicKey(seed)

    @get:Ignore
    val authorityKey: PublicKey
        get() = PublicKey(authority)

    @get:Ignore
    val mintVaultKey: PublicKey
        get() = PublicKey(mintVault)

    @get:Ignore
    val coreMintVaultKey: PublicKey
        get() = PublicKey(coreMintVault)

    @get:Ignore
    val price: Fiat
        get() = Fiat(fiat = priceAmount,)

    @get:Ignore
    val marketCap: Fiat
        get() = Fiat(fiat = marketCapAmount)
}