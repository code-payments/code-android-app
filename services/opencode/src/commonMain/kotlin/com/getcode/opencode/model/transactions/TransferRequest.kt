package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

sealed interface TransferRequest
data class GrabRequest(val account: PublicKey, val signature: Signature) : TransferRequest
data class GiveRequest(
    val messageId: PublicKey,
    val mint: Mint,
    val exchangeData: ExchangeData.Verified,
    val tokenMetadata: MintMetadata?
) : TransferRequest