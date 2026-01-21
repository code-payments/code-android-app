package com.getcode.opencode.internal.solana.extensions

import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.solana.keys.PublicKey

data class ExtractedServerParams(
    val payer: PublicKey,
    val alts: List<AddressLookupTable>,
    val computeUnitLimit: Int,
    val computeUnitPrice: Long,
    val memo: String,
    val memoryAccount: PublicKey,
    val memoryIndex: Int,
)

internal fun extractServerParameters(serverParameters: SwapResponseServerParameters): ExtractedServerParams {
    return ExtractedServerParams(
        payer = serverParameters.payer,
        alts = serverParameters.alts,
        computeUnitLimit = serverParameters.computeUnitLimit,
        computeUnitPrice = serverParameters.computeUnitPrice,
        memo = serverParameters.memoValue,
        memoryAccount = serverParameters.memoryAccount,
        memoryIndex = serverParameters.memoryIndex,
    )
}