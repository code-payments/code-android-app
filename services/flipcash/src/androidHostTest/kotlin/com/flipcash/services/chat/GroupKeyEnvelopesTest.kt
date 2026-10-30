package com.flipcash.services.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteKeyBundle
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.ed25519kmp.KeyPair
import com.getcode.solana.keys.PublicKey
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupKeyEnvelopesTest {

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val creatorKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val memberPk = ByteArray(32) { 21 }

    private val keys = object : ChatKeySource {
        override fun ownKeyPair() = creatorKeys
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> = error("unused")
    }
    private val controller = mockk<ChatController>()
    private val store = InMemoryGroupKeyEnvelopeStore()
    private val envelopes = GroupKeyEnvelopes(FakeChatCipher, FakeInviteCipher(), keys, controller, WrongKeyChats(), store)

    @Test
    fun `storeNewKey stores a self-wrapped envelope`() = runTest {
        val stored = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(stored)) } returns Result.success(Unit)

        assertTrue(envelopes.storeNewKey(chatId).isSuccess)

        assertEquals(KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305, stored.captured.scheme)
        val opened = FakeChatCipher.unwrapGroupKey(
            creatorKeys, creatorKeys.publicKey, chatId.bytes,
            EncryptedPayload(stored.captured.nonce, stored.captured.ciphertext),
        )
        assertContentEquals(FakeChatCipher.newGroupKey(), opened)
    }

    @Test
    fun `ALREADY_SET counts as stored`() = runTest {
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(SetKeyEnvelopeError.AlreadySet())

        assertTrue(envelopes.storeNewKey(chatId).isSuccess)
    }

    @Test
    fun `any other failure is returned`() = runTest {
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(IOException("offline"))

        assertTrue(envelopes.storeNewKey(chatId).isFailure)
    }

    @Test
    fun `wrapFor reuses the key just stored`() = runTest {
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.success(Unit)
        envelopes.storeNewKey(chatId)

        val envelope = envelopes.wrapFor(chatId, memberPk).getOrThrow()

        val member = KeyPair(publicKey = memberPk, privateKey = ByteArray(64))
        val opened = FakeChatCipher.unwrapGroupKey(
            member, creatorKeys.publicKey, chatId.bytes, EncryptedPayload(envelope.nonce, envelope.ciphertext),
        )
        assertContentEquals(FakeChatCipher.newGroupKey(), opened)
        coVerify(exactly = 0) { controller.getKeyEnvelope(any()) }
    }

    @Test
    fun `wrapFor opens the stored envelope when nothing is cached`() = runTest {
        val self = FakeChatCipher.wrapGroupKey(creatorKeys, creatorKeys.publicKey, chatId.bytes, ByteArray(32) { 5 })
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(
            StoredKeyEnvelope(KeyEnvelope(1, self.nonce, self.ciphertext), wrappedBy = null),
        )

        val envelope = envelopes.wrapFor(chatId, memberPk).getOrThrow()

        assertContentEquals(creatorKeys.publicKey + memberPk + ByteArray(32) { 5 }, envelope.ciphertext)
    }

    // Member path: the viewer is memberKeys, admitted by the creator.

    private val creatorId: ID = List(16) { 1 }
    private val memberId: ID = List(16) { 2 }
    private val memberKeys = KeyPair(publicKey = memberPk, privateKey = ByteArray(64) { 22 })
    private val inviteCipher = FakeInviteCipher()
    private val wrongKeyChats = WrongKeyChats()
    private val memberSource = object : ChatKeySource {
        override fun ownKeyPair() = memberKeys
        override fun ownUserId() = memberId
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> =
            if (userId == creatorId) Result.success(creatorKeys.publicKey) else Result.failure(IOException("unknown"))
    }
    private val memberEnvelopes = GroupKeyEnvelopes(FakeChatCipher, inviteCipher, memberSource, controller, wrongKeyChats, store)
    private val groupKey = ByteArray(32) { 5 }

    private fun stored(wrapper: KeyPair, recipient: ByteArray, wrappedBy: ID?): StoredKeyEnvelope {
        val sealed = FakeChatCipher.wrapGroupKey(wrapper, recipient, chatId.bytes, groupKey)
        return StoredKeyEnvelope(KeyEnvelope(1, sealed.nonce, sealed.ciphertext), wrappedBy)
    }

    private fun metadata(creator: ID?) = mockk<ChatMetadata> { every { this@mockk.creator } returns creator }

    @Test
    fun `a member opens the creator's envelope and stores a self-wrapped copy`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(creatorKeys, memberPk, creatorId))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        val rewrapped = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(rewrapped)) } returns Result.success(Unit)

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())

        assertContentEquals(memberPk + memberPk + groupKey, rewrapped.captured.ciphertext)
    }

    @Test
    fun `a failed re-wrap still returns the key`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(creatorKeys, memberPk, creatorId))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(IOException("offline"))

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
    }

    @Test
    fun `the key is fetched once`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(memberKeys, memberPk, memberId))

        memberEnvelopes.key(chatId).getOrThrow()
        memberEnvelopes.key(chatId).getOrThrow()

        coVerify(exactly = 1) { controller.getKeyEnvelope(chatId) }
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `an envelope wrapped by someone other than the creator is refused`() = runTest {
        val stranger: ID = List(16) { 9 }
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(creatorKeys, memberPk, stranger))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).exceptionOrNull() is UntrustedEnvelope)
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `an envelope that does not open under the creator's key is refused`() = runTest {
        val impostor = KeyPair(publicKey = ByteArray(32) { 99.toByte() }, privateKey = ByteArray(64))
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(impostor, memberPk, creatorId))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).exceptionOrNull() is UntrustedEnvelope)
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `ALREADY_SET on the re-wrap reads the envelope another device stored`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returnsMany listOf(
            Result.success(stored(creatorKeys, memberPk, creatorId)),
            Result.success(stored(memberKeys, memberPk, memberId)),
        )
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(SetKeyEnvelopeError.AlreadySet())

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
        coVerify(exactly = 2) { controller.getKeyEnvelope(chatId) }
    }

    @Test
    fun `NO_ENVELOPE regenerates the key only for the creator`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.NoEnvelope())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `NO_ENVELOPE for the creator stores a new key and uses it`() = runTest {
        val creatorSource = object : ChatKeySource {
            override fun ownKeyPair() = creatorKeys
            override fun ownUserId() = creatorId
            override suspend fun peerPublicKey(userId: ID): Result<ByteArray> = error("unused")
        }
        val creatorEnvelopes = GroupKeyEnvelopes(FakeChatCipher, FakeInviteCipher(), creatorSource, controller, WrongKeyChats(), store)
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.NoEnvelope())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.success(Unit)

        assertContentEquals(FakeChatCipher.newGroupKey(), creatorEnvelopes.key(chatId).getOrThrow())
    }

    // Invite path: the member joined with a link and the server holds only the invite's copy.

    private val secret = ByteArray(32) { 3 }
    private val inviteId = InviteId(inviteCipher.inviteId(secret))

    private fun bundle(
        signer: KeyPair = creatorKeys,
        wrappedBy: ID? = creatorId,
        secretOwner: KeyPair = memberKeys,
        sealedFor: ByteArray = secret,
        secretScheme: Int = InviteSecretEnvelope.SCHEME_X25519_XCHACHA20POLY1305,
        wrappedByPublicKey: PublicKey? = null,
    ): GetKeyEnvelopeError.InviteKeyBundleOnly {
        val copy = inviteCipher.sealInviteKeyEnvelope(signer, sealedFor, chatId.bytes, groupKey).toEnvelope()
        val own = inviteCipher.sealInviteSecretEnvelope(secretOwner, secret, chatId.bytes, inviteId.bytes)
        return GetKeyEnvelopeError.InviteKeyBundleOnly(
            InviteKeyBundle(
                inviteId = inviteId,
                keyEnvelope = copy,
                secretEnvelope = InviteSecretEnvelope(secretScheme, own.nonce, own.ciphertext),
                wrappedBy = wrappedBy,
                wrappedByPublicKey = wrappedByPublicKey,
            ),
        )
    }

    private fun assertWrongKey(error: GetKeyEnvelopeError.InviteKeyBundleOnly) = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(error)
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).exceptionOrNull() is UntrustedEnvelope)
        assertTrue(chatId in wrongKeyChats.chats.value)
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `an invite bundle opens and the member stores a self-wrapped copy`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        val rewrapped = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(rewrapped)) } returns Result.success(Unit)

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())

        assertContentEquals(memberPk + memberPk + groupKey, rewrapped.captured.ciphertext)
        assertFalse(chatId in wrongKeyChats.chats.value)
    }

    @Test
    fun `a failed store after the bundle opens still returns the key`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(IOException("offline"))

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
    }

    @Test
    fun `a bundle wrapped by someone other than the creator is the wrong key`() =
        assertWrongKey(bundle(wrappedBy = List(16) { 9 }))

    @Test
    fun `a bundle not signed by the creator is the wrong key`() =
        assertWrongKey(bundle(signer = KeyPair(publicKey = ByteArray(32) { 99.toByte() }, privateKey = ByteArray(64))))

    @Test
    fun `a bundle whose secret envelope does not open is the wrong key`() =
        assertWrongKey(bundle(secretOwner = KeyPair(publicKey = ByteArray(32) { 77 }, privateKey = ByteArray(64))))

    @Test
    fun `a bundle whose copy does not open is the wrong key`() =
        assertWrongKey(bundle(sealedFor = ByteArray(32) { 4 }))

    @Test
    fun `a bundle in an unknown scheme is refused without the wrong-key mark`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle(secretScheme = 9))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).exceptionOrNull() is UntrustedEnvelope)
        assertFalse(chatId in wrongKeyChats.chats.value)
        coVerify(exactly = 0) { controller.setKeyEnvelope(any(), any()) }
    }

    @Test
    fun `the reported creator key opens the bundle when the creator's key can't be looked up`() = runTest {
        val offline = object : ChatKeySource {
            override fun ownKeyPair() = memberKeys
            override fun ownUserId() = memberId
            override suspend fun peerPublicKey(userId: ID): Result<ByteArray> = Result.failure(IOException("offline"))
        }
        val envelopes = GroupKeyEnvelopes(FakeChatCipher, inviteCipher, offline, controller, wrongKeyChats, store)
        coEvery { controller.getKeyEnvelope(chatId) } returns
            Result.failure(bundle(wrappedByPublicKey = PublicKey(creatorKeys.publicKey.toList())))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.success(Unit)

        assertContentEquals(groupKey, envelopes.key(chatId).getOrThrow())
    }

    @Test
    fun `ALREADY_SET after the bundle opens reads the envelope another device stored`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returnsMany listOf(
            Result.failure(bundle()),
            Result.success(stored(memberKeys, memberPk, memberId)),
        )
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(SetKeyEnvelopeError.AlreadySet())

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
        coVerify(exactly = 2) { controller.getKeyEnvelope(chatId) }
    }

    @Test
    fun `a rejected bundle is not cached and is fetched again on the next open`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle(wrappedBy = List(16) { 9 }))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertTrue(memberEnvelopes.key(chatId).isFailure)
        coVerify(exactly = 2) { controller.getKeyEnvelope(chatId) }
    }

    @Test
    fun `a network failure is not cached and the next open retries`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returnsMany listOf(
            Result.failure(GetKeyEnvelopeError.Other(IOException("offline"))),
            Result.failure(bundle()),
        )
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.success(Unit)

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertFalse(chatId in wrongKeyChats.chats.value)

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
        coVerify(exactly = 2) { controller.getKeyEnvelope(chatId) }
    }

    @Test
    fun `a key that opens later clears the wrong-key mark`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returnsMany listOf(
            Result.failure(bundle(wrappedBy = List(16) { 9 })),
            Result.success(stored(memberKeys, memberPk, memberId)),
        )
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertTrue(chatId in wrongKeyChats.chats.value)

        memberEnvelopes.key(chatId).getOrThrow()
        assertFalse(chatId in wrongKeyChats.chats.value)
    }

    // Persistence: the envelope is stored as fetched, and the stored row follows the in-memory key.

    private val rowKey get() = chatId.bytes.toList() to memberPk.toList()
    private fun restarted() = GroupKeyEnvelopes(FakeChatCipher, inviteCipher, memberSource, controller, wrongKeyChats, store)

    private fun selfWrapped() = stored(memberKeys, memberPk, memberId)

    @Test
    fun `a network fetch stores the envelope as returned`() = runTest {
        val fetched = selfWrapped()
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(fetched)

        memberEnvelopes.key(chatId).getOrThrow()

        assertEquals(fetched, store.rows[rowKey])
    }

    @Test
    fun `the stored row holds the wrapped envelope and never the unwrapped key`() = runTest {
        val fetched = selfWrapped()
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(fetched)

        memberEnvelopes.key(chatId).getOrThrow()

        val row = store.rows.getValue(rowKey)
        assertContentEquals(fetched.envelope.nonce, row.envelope.nonce)
        assertContentEquals(fetched.envelope.ciphertext, row.envelope.ciphertext)
        assertFalse(row.envelope.nonce.contentEquals(groupKey))
        assertFalse(row.envelope.ciphertext.contentEquals(groupKey))
        assertTrue(row.wrappedBy == null || row.wrappedBy == memberId)
    }

    @Test
    fun `a stored envelope opens after a restart with the network failing`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(selfWrapped())
        memberEnvelopes.key(chatId).getOrThrow()

        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.Other(IOException("offline")))
        coEvery { controller.getChat(chatId, any()) } returns Result.failure(IOException("offline"))

        assertContentEquals(groupKey, restarted().key(chatId).getOrThrow())
        coVerify(exactly = 1) { controller.getKeyEnvelope(chatId) }
    }

    @Test
    fun `a stored creator-wrapped envelope that can't be checked offline is kept and the fetch failure returned`() = runTest {
        val creatorWrapped = stored(creatorKeys, memberPk, creatorId)
        store.rows[rowKey] = creatorWrapped
        coEvery { controller.getChat(chatId, any()) } returns Result.failure(IOException("offline"))
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.Other(IOException("offline")))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertEquals(creatorWrapped, store.rows[rowKey])
    }

    @Test
    fun `a network fetch replaces the stored envelope`() = runTest {
        store.rows[rowKey] = stored(creatorKeys, memberPk, creatorId)
        // The stored creator-wrapped copy opens and is re-wrapped: the self-wrapped copy replaces it.
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        val rewrapped = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(rewrapped)) } returns Result.success(Unit)

        memberEnvelopes.key(chatId).getOrThrow()

        assertEquals(rewrapped.captured, store.rows[rowKey]?.envelope)
        coVerify(exactly = 0) { controller.getKeyEnvelope(any()) }
    }

    @Test
    fun `a stored envelope that fails to unwrap falls back to the network and is replaced`() = runTest {
        val impostor = KeyPair(publicKey = ByteArray(32) { 99.toByte() }, privateKey = ByteArray(64))
        store.rows[rowKey] = stored(impostor, memberPk, memberId)
        val fetched = selfWrapped()
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(fetched)

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())

        coVerify(exactly = 1) { controller.getKeyEnvelope(chatId) }
        assertEquals(fetched, store.rows[rowKey])
    }

    @Test
    fun `a stored envelope in an unknown scheme is dropped and the network used`() = runTest {
        val sealed = selfWrapped().envelope
        store.rows[rowKey] = StoredKeyEnvelope(KeyEnvelope(99, sealed.nonce, sealed.ciphertext), wrappedBy = null)
        val fetched = selfWrapped()
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(fetched)

        memberEnvelopes.key(chatId).getOrThrow()

        assertEquals(fetched, store.rows[rowKey])
    }

    @Test
    fun `a stored envelope wrapped by a stranger is dropped and the network used`() = runTest {
        store.rows[rowKey] = stored(creatorKeys, memberPk, List(16) { 9 })
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        val fetched = selfWrapped()
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(fetched)

        memberEnvelopes.key(chatId).getOrThrow()

        assertEquals(fetched, store.rows[rowKey])
    }

    @Test
    fun `an untrusted fetched envelope clears the stored row`() = runTest {
        val impostor = KeyPair(publicKey = ByteArray(32) { 99.toByte() }, privateKey = ByteArray(64))
        store.rows[rowKey] = stored(impostor, memberPk, memberId)
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(stored(impostor, memberPk, memberId))

        assertTrue(memberEnvelopes.key(chatId).exceptionOrNull() is UntrustedEnvelope)
        assertEquals(null, store.rows[rowKey])
    }

    @Test
    fun `a wrong-key invite bundle clears the stored row`() = runTest {
        store.rows[rowKey] = stored(memberKeys, memberPk, List(16) { 9 }) // fails the wrapper check
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle(wrappedBy = List(16) { 9 }))
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertTrue(chatId in wrongKeyChats.chats.value)
        assertEquals(null, store.rows[rowKey])
    }

    @Test
    fun `an invite bundle's copy is replaced by the member's own stored envelope`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        val rewrapped = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(rewrapped)) } returns Result.success(Unit)

        memberEnvelopes.key(chatId).getOrThrow()

        assertEquals(rewrapped.captured, store.rows[rowKey]?.envelope)
        assertContentEquals(memberPk + memberPk + groupKey, store.rows.getValue(rowKey).envelope.ciphertext)
    }

    @Test
    fun `an invite bundle whose own copy can't be stored leaves no stored row`() = runTest {
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(bundle())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(IOException("offline"))

        assertContentEquals(groupKey, memberEnvelopes.key(chatId).getOrThrow())
        assertEquals(null, store.rows[rowKey])
    }

    @Test
    fun `NO_ENVELOPE clears a stored row for a non-creator`() = runTest {
        store.rows[rowKey] = stored(impostorKeys(), memberPk, memberId)
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.NoEnvelope())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertEquals(null, store.rows[rowKey])
    }

    private fun impostorKeys() = KeyPair(publicKey = ByteArray(32) { 99.toByte() }, privateKey = ByteArray(64))

    @Test
    fun `a failed fetch other than a refusal keeps the stored row`() = runTest {
        // A stored creator-wrapped copy that needs the network to check, with the network down.
        val kept = stored(creatorKeys, memberPk, creatorId)
        store.rows[rowKey] = kept
        coEvery { controller.getChat(chatId, any()) } returns Result.failure(IOException("offline"))
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.Other(IOException("offline")))

        assertTrue(memberEnvelopes.key(chatId).isFailure)
        assertEquals(kept, store.rows[rowKey])
    }

    @Test
    fun `storeNewKey stores the creator's envelope`() = runTest {
        val sent = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(sent)) } returns Result.success(Unit)

        envelopes.storeNewKey(chatId).getOrThrow()

        val row = store.rows[chatId.bytes.toList() to creatorKeys.publicKey.toList()]
        assertEquals(sent.captured, row?.envelope)
        assertFalse(row!!.envelope.ciphertext.contentEquals(FakeChatCipher.newGroupKey()))
    }

    @Test
    fun `storeNewKey replaces an older stored envelope`() = runTest {
        val rowKey = chatId.bytes.toList() to creatorKeys.publicKey.toList()
        store.rows[rowKey] = stored(creatorKeys, creatorKeys.publicKey, null)
        val sent = slot<KeyEnvelope>()
        coEvery { controller.setKeyEnvelope(chatId, capture(sent)) } returns Result.success(Unit)

        envelopes.storeNewKey(chatId).getOrThrow()

        assertEquals(sent.captured, store.rows[rowKey]?.envelope)
    }

    @Test
    fun `ALREADY_SET on storeNewKey clears the stored row`() = runTest {
        val rowKey = chatId.bytes.toList() to creatorKeys.publicKey.toList()
        store.rows[rowKey] = stored(creatorKeys, creatorKeys.publicKey, null)
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.failure(SetKeyEnvelopeError.AlreadySet())

        assertTrue(envelopes.storeNewKey(chatId).isSuccess)
        assertEquals(null, store.rows[rowKey])
    }

    @Test
    fun `a failing store never costs the key`() = runTest {
        val broken = object : GroupKeyEnvelopeStore {
            override suspend fun load(chatId: ChatId, ownPublicKey: ByteArray): StoredKeyEnvelope? = error("db")
            override suspend fun save(chatId: ChatId, ownPublicKey: ByteArray, envelope: StoredKeyEnvelope) = error("db")
            override suspend fun delete(chatId: ChatId, ownPublicKey: ByteArray) = error("db")
            override suspend fun clear() = error("db")
        }
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.success(selfWrapped())

        val opened = GroupKeyEnvelopes(FakeChatCipher, inviteCipher, memberSource, controller, wrongKeyChats, broken)
        assertContentEquals(groupKey, opened.key(chatId).getOrThrow())
    }
}
