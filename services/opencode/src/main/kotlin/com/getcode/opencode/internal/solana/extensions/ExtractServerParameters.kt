package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.solana.keys.PublicKey

sealed interface ExtractedServerParams {
    val payer: PublicKey
    val alts: List<AddressLookupTable>
    val computeUnitLimit: Int
    val computeUnitPrice: Long
    val memo: String

    data class ExistingCurrency(
        override val payer: PublicKey,
        override val alts: List<AddressLookupTable>,
        override val computeUnitLimit: Int,
        override val computeUnitPrice: Long,
        override val memo: String,
        val memoryAccount: PublicKey,
        val memoryIndex: Int,
    ): ExtractedServerParams

    data class NewCurrency(
        override val payer: PublicKey,
        override val alts: List<AddressLookupTable>,
        override val computeUnitLimit: Int,
        override val computeUnitPrice: Long,
        override val memo: String,
        val authority: PublicKey,
        val name: String,
        val symbol: String,
        val seed: PublicKey,
        val sellFeeBps: Int,
        val vmLockDurationInDays: Int,
    ): ExtractedServerParams
}

internal fun extractServerParameters(serverParameters: SwapResponseServerParameters.ExistingCurrency): ExtractedServerParams.ExistingCurrency {
    return ExtractedServerParams.ExistingCurrency(
        payer = serverParameters.payer,
        alts = serverParameters.alts,
        computeUnitLimit = serverParameters.computeUnitLimit,
        computeUnitPrice = serverParameters.computeUnitPrice,
        memo = serverParameters.memoValue,
        memoryAccount = serverParameters.memoryAccount,
        memoryIndex = serverParameters.memoryIndex,
    )
}

internal fun extractServerParameters(serverParameters: SwapResponseServerParameters.NewCurrency): ExtractedServerParams.NewCurrency {
    return ExtractedServerParams.NewCurrency(
        payer = serverParameters.payer,
        alts = serverParameters.alts,
        computeUnitLimit = serverParameters.computeUnitLimit,
        computeUnitPrice = serverParameters.computeUnitPrice,
        memo = serverParameters.memoValue,
        authority = serverParameters.authority,
        name = serverParameters.name,
        symbol = serverParameters.symbol,
        seed = serverParameters.seed,
        sellFeeBps = serverParameters.sellFeeBps,
        vmLockDurationInDays = serverParameters.vmLockDurationInDays,
    )
}