package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.StoredKeyEnvelope

/**
 * Where [GroupKeyEnvelopes] keeps each private group's key envelope between launches, so a chat
 * opens offline. Holds the envelope as the server returned it, still wrapped; the chat key is never
 * passed to it. One entry per chat and own public key.
 */
interface GroupKeyEnvelopeStore {
    suspend fun load(chatId: ChatId, ownPublicKey: ByteArray): StoredKeyEnvelope?

    /** Stores [envelope], replacing any earlier one for the same chat and key. */
    suspend fun save(chatId: ChatId, ownPublicKey: ByteArray, envelope: StoredKeyEnvelope)

    suspend fun delete(chatId: ChatId, ownPublicKey: ByteArray)

    suspend fun clear()
}
