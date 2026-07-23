package com.flipcash.shared.chat.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID
import java.security.MessageDigest
import javax.inject.Inject

class ChatIdGenerator @Inject constructor() {

    /**
     * Derives the canonical DM chat id for [chatType] between users [a] and [b],
     * mirroring the server's `MustDeriveDmChatID(chatType, a, b)`. Stable and
     * order-independent, so either user derives the same id without a lookup.
     */
    fun generate(chatType: ChatType, a: ID, b: ID): ChatId =
        compute(chatType.dmDomain(), a.toByteArray(), b.toByteArray())

    /**
     * Domain separator for [this] DM chat type. Contact DMs hash under the bare
     * legacy prefix (their ids predate typed derivation and must not change);
     * every other DM type appends its `ChatType` enum value. Mirrors
     * flipcash2-server `chat/model.go`.
     */
    private fun ChatType.dmDomain(): String = when (this) {
        ChatType.CONTACT_DM -> DM_DOMAIN
        ChatType.TIP_DM -> TIP_DM_DOMAIN
        ChatType.UNKNOWN -> error("cannot derive a DM chat id for chat type $this")
    }

    /**
     * Mirrors the server's derivation byte-for-byte: SHA-256 over the [domain]
     * followed by the unsigned-lexicographically sorted member set, where a
     * self-pair (a == b) collapses to a single member. The server rejects any
     * intent whose chat id doesn't match, so this is a wire contract and must
     * not diverge.
     */
    private fun compute(domain: String, a: ByteArray, b: ByteArray): ChatId {
        val (first, second) = if (a.compareUnsigned(b) <= 0) a to b else b to a
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(domain.toByteArray(Charsets.UTF_8))
            update(first)
            if (!first.contentEquals(second)) update(second)
            digest()
        }
        return ChatId(digest)
    }

    /** Lexicographic comparison treating bytes as unsigned, like Solana key ordering. */
    private fun ByteArray.compareUnsigned(other: ByteArray): Int {
        val shared = minOf(size, other.size)
        for (i in 0 until shared) {
            val diff = (this[i].toInt() and 0xFF) - (other[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return size - other.size
    }

    private companion object {
        /** Bare legacy domain: contact DM ids predate typed derivation. */
        const val DM_DOMAIN = "flipcash:chat:dm"

        /** TIP_DM appends its ChatType enum value (2) to the base domain. */
        const val TIP_DM_DOMAIN = "$DM_DOMAIN:2"
    }
}