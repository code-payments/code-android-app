package com.flipcash.services.models.chat

import com.getcode.utils.base58

data class ChatId(val bytes: ByteArray) {

    constructor(bytes: List<Byte>): this(bytes.toByteArray())
    @OptIn(ExperimentalStdlibApi::class)
    constructor(hex: String): this(hex.hexToByteArray())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatId) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = bytes.base58
}
