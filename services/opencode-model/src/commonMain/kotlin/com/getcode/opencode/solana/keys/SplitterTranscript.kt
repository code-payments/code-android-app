package com.getcode.opencode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.toUTF8Bytes

data class SplitterTranscript(
    val intentId: PublicKey,
    val actionId: Int, val
    amount: Fiat,
    val source: PublicKey,
    val destination: PublicKey
) {
    val description =
        "receipt[${intentId.base58()}, $actionId]: " +
                "transfer ${amount.quarks} quarks " +
                "from ${source.base58()} to ${destination.base58()}"

    val transcriptHash: Hash =
        Hash(Sha256Hash.hash(description.toUTF8Bytes()).toList())
}
