package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

data class TransferRequest(val account: PublicKey, val signature: Signature)