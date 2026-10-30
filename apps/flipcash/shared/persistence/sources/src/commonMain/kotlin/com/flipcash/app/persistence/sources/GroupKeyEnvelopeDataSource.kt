package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.GroupKeyEnvelopeEntity
import com.flipcash.services.chat.GroupKeyEnvelopeStore
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.hexEncodedString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `group_key_envelopes` table: each private group's key envelope as the server returned it.
 * No database means nothing stored rather than an error, like the other data sources here.
 */
@Singleton
class GroupKeyEnvelopeDataSource @Inject constructor() : GroupKeyEnvelopeStore {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    override suspend fun load(chatId: ChatId, ownPublicKey: ByteArray): StoredKeyEnvelope? =
        db?.groupKeyEnvelopeDao()?.find(chatId.hex(), ownPublicKey.hex())?.toStored()

    override suspend fun save(chatId: ChatId, ownPublicKey: ByteArray, envelope: StoredKeyEnvelope) {
        db?.groupKeyEnvelopeDao()?.upsert(
            GroupKeyEnvelopeEntity(
                chatIdHex = chatId.hex(),
                ownPkHex = ownPublicKey.hex(),
                scheme = envelope.envelope.scheme,
                nonce = envelope.envelope.nonce,
                ciphertext = envelope.envelope.ciphertext,
                wrappedBy = envelope.wrappedBy?.toByteArray(),
                wrappedByPublicKey = envelope.wrappedByPublicKey?.byteArray,
            )
        )
    }

    override suspend fun delete(chatId: ChatId, ownPublicKey: ByteArray) {
        db?.groupKeyEnvelopeDao()?.delete(chatId.hex(), ownPublicKey.hex())
    }

    override suspend fun clear() {
        db?.groupKeyEnvelopeDao()?.deleteAll()
    }

    private fun ChatId.hex() = bytes.hex()
    private fun ByteArray.hex() = toList().hexEncodedString()

    private fun GroupKeyEnvelopeEntity.toStored() = StoredKeyEnvelope(
        envelope = KeyEnvelope(scheme = scheme, nonce = nonce, ciphertext = ciphertext),
        wrappedBy = wrappedBy?.toList(),
        wrappedByPublicKey = wrappedByPublicKey?.let { PublicKey(it.toList()) },
    )
}
