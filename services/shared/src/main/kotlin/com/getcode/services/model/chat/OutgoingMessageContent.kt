package com.getcode.services.model.chat

import com.getcode.solana.keys.PublicKey

sealed interface OutgoingMessageContent {
    data class Text(val text: String): OutgoingMessageContent
    data class LocalizedText(val key: String): OutgoingMessageContent
    data class Encrypted(val publicKey: PublicKey, val nonce: List<Byte>, val payload: List<Byte>):
        OutgoingMessageContent
}