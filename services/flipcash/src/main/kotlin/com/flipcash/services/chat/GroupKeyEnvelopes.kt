package com.flipcash.services.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.opencode.model.core.ID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A private group's chat key, carried to each member as a `chat.v1.KeyEnvelope`.
 *
 * The creator generates the key once the chat exists ([storeNewKey]) and wraps it for each user it
 * admits ([wrapFor]). Wrapping reuses the DM cipher's key agreement under the group-wrap label; the
 * server only ever sees envelopes.
 */
@Singleton
class GroupKeyEnvelopes @Inject constructor(
    private val cipher: ChatCipher,
    private val keys: ChatKeySource,
    private val chatController: ChatController,
) {
    // Own public key in the key so a different account on this device never reuses a group key.
    private data class CacheKey(val chatId: ID, val ownPk: ID)

    private val groupKeys = ConcurrentHashMap<CacheKey, ByteArray>()

    /**
     * Generates [chatId]'s chat key and stores the creator's own envelope: the second step of
     * creating a private group. `ALREADY_SET` is success, since the envelope stored first stands
     * and a retry after a lost response lands there.
     */
    suspend fun storeNewKey(chatId: ChatId): Result<Unit> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val groupKey = cipher.newGroupKey()
        val envelope = runCatching {
            cipher.wrapGroupKey(own, own.publicKey, chatId.bytes, groupKey).toKeyEnvelope()
        }.getOrElse { return Result.failure(it) }

        val cacheKey = CacheKey(chatId.bytes.toList(), own.publicKey.toList())
        return chatController.setKeyEnvelope(chatId, envelope)
            .onSuccess { groupKeys[cacheKey] = groupKey }
            .recoverCatching { error ->
                if (error is SetKeyEnvelopeError.AlreadySet) groupKeys.remove(cacheKey) else throw error
            }
    }

    /** Wraps [chatId]'s chat key for [recipientPk], for the creator to admit them. */
    suspend fun wrapFor(chatId: ChatId, recipientPk: ByteArray): Result<KeyEnvelope> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val groupKey = groupKey(chatId).getOrElse { return Result.failure(it) }
        return runCatching { cipher.wrapGroupKey(own, recipientPk, chatId.bytes, groupKey).toKeyEnvelope() }
    }

    /**
     * The viewer's copy of [chatId]'s chat key, opened from their stored envelope. Only a
     * self-wrapped envelope is opened here; one the creator wrapped for a member is the member's
     * path, which needs the creator's key.
     */
    private suspend fun groupKey(chatId: ChatId): Result<ByteArray> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val cacheKey = CacheKey(chatId.bytes.toList(), own.publicKey.toList())
        groupKeys[cacheKey]?.let { return Result.success(it) }

        val stored = chatController.getKeyEnvelope(chatId).getOrElse { return Result.failure(it) }
        return runCatching {
            cipher.unwrapGroupKey(
                ownKeyPair = own,
                wrapperPk = own.publicKey,
                chatId = chatId.bytes,
                envelope = EncryptedPayload(stored.envelope.nonce, stored.envelope.ciphertext),
            )
        }.onSuccess { groupKeys[cacheKey] = it }
    }

    private fun EncryptedPayload.toKeyEnvelope() = KeyEnvelope(
        scheme = KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305,
        nonce = nonce,
        ciphertext = ciphertext,
    )
}
