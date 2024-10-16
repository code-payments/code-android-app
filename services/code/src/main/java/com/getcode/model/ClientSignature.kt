package com.getcode.model

import com.getcode.solana.keys.Signature

data class ClientSignature(
    val transaction: Signature,
    val signature: Signature
)
