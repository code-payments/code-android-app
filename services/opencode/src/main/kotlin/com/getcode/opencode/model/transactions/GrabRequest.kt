package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
sealed interface TransferRequest
data class GrabRequest(val account: PublicKey, val signature: Signature): TransferRequest
data class GiveRequest(val mint: Mint): TransferRequest