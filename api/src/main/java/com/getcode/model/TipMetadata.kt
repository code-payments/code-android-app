package com.getcode.model

import com.getcode.solana.keys.PublicKey

sealed interface TipMetadata {
    val platform: String
    val username: String
    val tipAddress: PublicKey
}