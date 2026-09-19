package com.getcode.opencode.internal.solana.model

import com.getcode.opencode.internal.solana.ShortVec
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58

data class MessageAddressLookupTable(
    val publicKey: PublicKey,
    val writableIndexes: List<Byte>,
    val readonlyIndexes: List<Byte>,
) {
    val description: String
        get() = "Lookup(publicKey: ${publicKey.base58()}, writableIndexes: ${writableIndexes.map { it.toInt() }}, readonlyIndexes: ${readonlyIndexes.map { it.toInt() }})"

    fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(publicKey.bytes)
        data.addAll(ShortVec.encodeLen(writableIndexes.count()))
        data.addAll(writableIndexes)
        data.addAll(ShortVec.encodeLen(readonlyIndexes.count()))
        data.addAll(readonlyIndexes)
        return data
    }
}