package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.PublicKey

data class AddressLookupTable(
    val publicKey: PublicKey,
    val addresses: List<PublicKey>,
)