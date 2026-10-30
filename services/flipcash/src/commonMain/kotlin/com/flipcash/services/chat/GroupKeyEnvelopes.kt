package com.flipcash.services.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.InviteKeyBundle
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.getcode.chatcipher.ChatCipher
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.chatcipher.InviteBundleResult
import com.getcode.chatcipher.InviteCipher
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val inviteCipher: InviteCipher,
    private val keys: ChatKeySource,
    private val chatController: ChatController,
    private val wrongKeyChats: WrongKeyChats,
    private val store: GroupKeyEnvelopeStore,
) : GroupKeySource {
    // Own public key in the key so a different account on this device never reuses a group key.
    private data class CacheKey(val chatId: ID, val ownPk: ID)

    private val groupKeys = ConcurrentHashMap<CacheKey, ByteArray>()
    private val locks = ConcurrentHashMap<CacheKey, Mutex>()

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
            .onSuccess {
                saveEnvelope(chatId, own, StoredKeyEnvelope(envelope, wrappedBy = null))
                groupKeys[cacheKey] = groupKey
            }
            .recoverCatching { error ->
                if (error is SetKeyEnvelopeError.AlreadySet) {
                    // The envelope stored first stands, and it isn't this one: neither the cache nor
                    // the stored row may keep a key generated here.
                    groupKeys.remove(cacheKey)
                    dropEnvelope(chatId, own)
                } else throw error
            }
    }

    /** Wraps [chatId]'s chat key for [recipientPk], for the creator to admit them. */
    suspend fun wrapFor(chatId: ChatId, recipientPk: ByteArray): Result<KeyEnvelope> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val groupKey = key(chatId).getOrElse { return Result.failure(it) }
        return runCatching { cipher.wrapGroupKey(own, recipientPk, chatId.bytes, groupKey).toKeyEnvelope() }
    }

    /**
     * The viewer's copy of [chatId]'s chat key, kept in memory. On a miss the envelope stored by an
     * earlier launch is opened first, with the same checks as a fetched one and no network call; if
     * there is none, or it fails a check, the envelope is fetched and replaces the stored one. Only
     * the envelope is stored, never the key.
     *
     * A self-wrapped envelope opens under the viewer's own key. One the creator wrapped when
     * admitting the viewer opens under the creator's key, which comes from the Resolver for
     * `Metadata.creator`; the member then wraps the key for itself and stores that, so its other
     * devices depend only on its own key. An envelope wrapped by anyone else is refused, and so is
     * one that fails to open: the caller must not send in the chat without this key.
     *
     * `NO_ENVELOPE` means a creator whose setup stopped before [storeNewKey] landed; only the
     * creator regenerates the key there.
     *
     * A member who joined with an invite link gets the invite's copy of the key until they store
     * their own envelope. It is opened and re-wrapped the same way; a copy that fails a check marks
     * the chat in [WrongKeyChats]. A fetch that fails for any other reason is not cached, so the
     * next call, such as opening the chat, tries again.
     */
    override suspend fun key(chatId: ChatId): Result<ByteArray> {
        val own = keys.ownKeyPair()
            ?: return Result.failure(IllegalStateException("No account key pair"))
        val cacheKey = CacheKey(chatId.bytes.toList(), own.publicKey.toList())
        groupKeys[cacheKey]?.let { return Result.success(it) }

        return locks.getOrPut(cacheKey) { Mutex() }.withLock {
            groupKeys[cacheKey]?.let { return@withLock Result.success(it) }
            openStored(chatId, own)?.let {
                groupKeys[cacheKey] = it
                wrongKeyChats.clear(chatId)
                return@withLock Result.success(it)
            }
            fetchKey(chatId, own)
                .onSuccess {
                    groupKeys[cacheKey] = it
                    wrongKeyChats.clear(chatId)
                }
                // Covers every refusal below, including the wrong-key mark: a stored envelope this
                // client won't trust must not open the chat on the next launch.
                .onFailure { if (it is UntrustedEnvelope) dropEnvelope(chatId, own) }
        }
    }

    /**
     * The key from the envelope stored for [chatId], or null when there is none or it can't be
     * used (the caller then fetches). One that fails a trust check is dropped; one that merely
     * couldn't be checked yet, such as a creator-wrapped envelope offline, is kept.
     */
    private suspend fun openStored(chatId: ChatId, own: KeyPair): ByteArray? {
        val stored = loadEnvelope(chatId, own) ?: return null
        return openEnvelope(chatId, own, stored, retried = false)
            .onFailure { if (it is UntrustedEnvelope) dropEnvelope(chatId, own) }
            .getOrNull()
    }

    private suspend fun fetchKey(chatId: ChatId, own: KeyPair, retried: Boolean = false): Result<ByteArray> {
        val stored = chatController.getKeyEnvelope(chatId).getOrElse { error ->
            if (error is GetKeyEnvelopeError.NoEnvelope) {
                if (!retried && viewerIsCreator(chatId)) {
                    // storeNewKey caches the key it stored; ALREADY_SET leaves nothing cached, so read that one.
                    return storeNewKey(chatId).mapCatching {
                        groupKeys[CacheKey(chatId.bytes.toList(), own.publicKey.toList())]
                            ?: fetchKey(chatId, own, retried = true).getOrThrow()
                    }
                }
                // The server holds nothing for the viewer, so a stored envelope is stale.
                dropEnvelope(chatId, own)
            }
            if (error is GetKeyEnvelopeError.InviteKeyBundleOnly) {
                return openInviteBundle(chatId, own, error.bundle, retried)
            }
            return Result.failure(error)
        }
        return openEnvelope(chatId, own, stored, retried)
    }

    /** Checks and opens [stored], whether it was just fetched or read back from the store. */
    private suspend fun openEnvelope(
        chatId: ChatId,
        own: KeyPair,
        stored: StoredKeyEnvelope,
        retried: Boolean,
    ): Result<ByteArray> {
        if (stored.envelope.scheme != KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305) {
            return Result.failure(UntrustedEnvelope("Unsupported envelope scheme ${stored.envelope.scheme}"))
        }
        val payload = EncryptedPayload(stored.envelope.nonce, stored.envelope.ciphertext)
        val wrapper = stored.wrappedBy

        if (wrapper == null || wrapper == keys.ownUserId()) {
            return runCatching { cipher.unwrapGroupKey(own, own.publicKey, chatId.bytes, payload) }
                .recoverCatching { throw UntrustedEnvelope("Self-wrapped envelope did not open", it) }
                .onSuccess { saveEnvelope(chatId, own, stored) }
        }

        val creator = chatController.getChat(chatId).getOrElse { return Result.failure(it) }.creator
        if (creator == null || wrapper != creator) {
            return Result.failure(UntrustedEnvelope("Envelope wrapped by someone other than the creator"))
        }
        val creatorPk = keys.peerPublicKey(creator).getOrElse { return Result.failure(it) }
        val groupKey = runCatching { cipher.unwrapGroupKey(own, creatorPk, chatId.bytes, payload) }
            .getOrElse { return Result.failure(UntrustedEnvelope("Creator-wrapped envelope did not open", it)) }
        return storeSelfCopy(chatId, own, groupKey, retried, keepOnFailure = stored)
    }

    /**
     * Opens the invite's copy of the key in the order the `InviteKeyEnvelope` comment sets. The
     * creator's key comes from the Resolver like a creator-wrapped envelope's, and falls back to the
     * `wrapped_by_public_key` the server reports when the Resolver can't answer.
     */
    private suspend fun openInviteBundle(
        chatId: ChatId,
        own: KeyPair,
        bundle: InviteKeyBundle,
        retried: Boolean,
    ): Result<ByteArray> {
        val creator = chatController.getChat(chatId).getOrElse { return Result.failure(it) }.creator
        val creatorPk = creator?.let { keys.peerPublicKey(it) }?.getOrNull()
            ?: bundle.wrappedByPublicKey?.byteArray
            ?: return Result.failure(IllegalStateException("No public key for the invite's creator"))
        val keyEnvelope = bundle.keyEnvelope.toSealed()
        val secretEnvelope = bundle.secretEnvelope.toPayload()
        // A scheme this build doesn't implement says nothing about the key, so it is refused the
        // way an unsupported KeyEnvelope is, without the wrong-key mark.
        if (keyEnvelope == null || secretEnvelope == null) {
            return Result.failure(UntrustedEnvelope("Unsupported invite envelope scheme"))
        }

        val opened = inviteCipher.openInviteKeyBundle(
            member = own,
            creatorPk = creatorPk,
            wrappedByCreator = creator != null && bundle.wrappedBy == creator,
            chatId = chatId.bytes,
            inviteId = bundle.inviteId.bytes,
            keyEnvelope = keyEnvelope,
            secretEnvelope = secretEnvelope,
        )
        return when (opened) {
            is InviteBundleResult.Opened -> storeSelfCopy(chatId, own, opened.chatKey, retried, keepOnFailure = null)
            is InviteBundleResult.Rejected -> wrongKey(chatId, "Invite key bundle rejected at ${opened.failsAt}")
        }
    }

    private fun wrongKey(chatId: ChatId, reason: String): Result<ByteArray> {
        wrongKeyChats.mark(chatId)
        return Result.failure(UntrustedEnvelope(reason))
    }

    /**
     * Stores the member's own copy of the key on the server and, with it, in [store]: the self-wrapped
     * envelope replaces the creator's or the invite's copy there. [keepOnFailure] is the envelope the
     * server still holds when the copy can't be stored (null for an invite's bundle, which is not a
     * `KeyEnvelope` and is never stored).
     */
    private suspend fun storeSelfCopy(
        chatId: ChatId,
        own: KeyPair,
        groupKey: ByteArray,
        retried: Boolean,
        keepOnFailure: StoredKeyEnvelope?,
    ): Result<ByteArray> {
        // The self-wrapped copy only saves the next launch a trip through the creator's envelope, so
        // failing to store it doesn't cost the key that just opened.
        val selfWrapped = runCatching {
            cipher.wrapGroupKey(own, own.publicKey, chatId.bytes, groupKey).toKeyEnvelope()
        }.getOrElse {
            keepOrDrop(chatId, own, keepOnFailure)
            return Result.success(groupKey)
        }
        return chatController.setKeyEnvelope(chatId, selfWrapped).fold(
            onSuccess = {
                saveEnvelope(chatId, own, StoredKeyEnvelope(selfWrapped, wrappedBy = null))
                Result.success(groupKey)
            },
            onFailure = { error ->
                // Another device re-wrapped first; its envelope stands, so read that one.
                if (error is SetKeyEnvelopeError.AlreadySet && !retried) fetchKey(chatId, own, retried = true)
                else {
                    keepOrDrop(chatId, own, keepOnFailure)
                    Result.success(groupKey)
                }
            },
        )
    }

    private suspend fun keepOrDrop(chatId: ChatId, own: KeyPair, envelope: StoredKeyEnvelope?) {
        if (envelope != null) saveEnvelope(chatId, own, envelope) else dropEnvelope(chatId, own)
    }

    // The store is a cache of the server's envelope: a failing database costs the offline open, never
    // the key, so its errors are swallowed.
    private suspend fun loadEnvelope(chatId: ChatId, own: KeyPair): StoredKeyEnvelope? =
        try { store.load(chatId, own.publicKey) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun saveEnvelope(chatId: ChatId, own: KeyPair, envelope: StoredKeyEnvelope) {
        try { store.save(chatId, own.publicKey, envelope) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    private suspend fun dropEnvelope(chatId: ChatId, own: KeyPair) {
        try { store.delete(chatId, own.publicKey) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    private suspend fun viewerIsCreator(chatId: ChatId): Boolean {
        val self = keys.ownUserId() ?: return false
        return chatController.getChat(chatId).getOrNull()?.creator == self
    }

    private fun EncryptedPayload.toKeyEnvelope() = KeyEnvelope(
        scheme = KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305,
        nonce = nonce,
        ciphertext = ciphertext,
    )
}

/** A key envelope this client will not trust: wrong wrapper, unknown scheme, or it failed to open. */
class UntrustedEnvelope(message: String, cause: Throwable? = null) : Exception(message, cause)
