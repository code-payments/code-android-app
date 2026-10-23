package com.flipcash.services.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.ed25519kmp.KeyPair
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
    private val envelopes = GroupKeyEnvelopes(FakeChatCipher, keys, controller)

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
    private val memberSource = object : ChatKeySource {
        override fun ownKeyPair() = memberKeys
        override fun ownUserId() = memberId
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> =
            if (userId == creatorId) Result.success(creatorKeys.publicKey) else Result.failure(IOException("unknown"))
    }
    private val memberEnvelopes = GroupKeyEnvelopes(FakeChatCipher, memberSource, controller)
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
        val creatorEnvelopes = GroupKeyEnvelopes(FakeChatCipher, creatorSource, controller)
        coEvery { controller.getKeyEnvelope(chatId) } returns Result.failure(GetKeyEnvelopeError.NoEnvelope())
        coEvery { controller.getChat(chatId, any()) } returns Result.success(metadata(creatorId))
        coEvery { controller.setKeyEnvelope(chatId, any()) } returns Result.success(Unit)

        assertContentEquals(FakeChatCipher.newGroupKey(), creatorEnvelopes.key(chatId).getOrThrow())
    }
}
