package com.flipcash.services.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.coVerify
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
}
