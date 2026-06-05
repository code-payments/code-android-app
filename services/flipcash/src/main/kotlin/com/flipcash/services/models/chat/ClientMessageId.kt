package com.flipcash.services.models.chat

data class ClientMessageId(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClientMessageId) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ClientMessageId(${bytes.size} bytes)"
}
