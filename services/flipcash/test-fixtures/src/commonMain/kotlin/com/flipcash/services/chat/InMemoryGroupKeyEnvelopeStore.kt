package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.StoredKeyEnvelope

/** A [GroupKeyEnvelopeStore] over a map, for tests; a second instance over the same map is a restart. */
class InMemoryGroupKeyEnvelopeStore(
    val rows: MutableMap<Pair<List<Byte>, List<Byte>>, StoredKeyEnvelope> = mutableMapOf(),
) : GroupKeyEnvelopeStore {
    private fun key(chatId: ChatId, ownPk: ByteArray) = chatId.bytes.toList() to ownPk.toList()

    override suspend fun load(chatId: ChatId, ownPublicKey: ByteArray) = rows[key(chatId, ownPublicKey)]

    override suspend fun save(chatId: ChatId, ownPublicKey: ByteArray, envelope: StoredKeyEnvelope) {
        rows[key(chatId, ownPublicKey)] = envelope
    }

    override suspend fun delete(chatId: ChatId, ownPublicKey: ByteArray) {
        rows.remove(key(chatId, ownPublicKey))
    }

    override suspend fun clear() = rows.clear()
}
