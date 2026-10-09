package com.getcode.chatcipher

import com.getcode.ed25519kmp.Ed25519Kmp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `chat.v1.KeyEnvelope` scheme `X25519_XCHACHA20POLY1305`. There are no shared vectors for it yet,
 * so these pin the properties the contract states rather than exact bytes.
 */
class GroupKeyWrapTest {
    private val creator = Ed25519Kmp.createKeyPair(ByteArray(32) { 1 })
    private val member = Ed25519Kmp.createKeyPair(ByteArray(32) { 2 })
    private val stranger = Ed25519Kmp.createKeyPair(ByteArray(32) { 3 })
    private val chatId = ByteArray(32) { it.toByte() }
    private val groupKey = ByteArray(32) { (0xA0 + it).toByte() }

    @Test
    fun creatorWrap_opensForTheRecipient() {
        val envelope = DefaultChatCipher.wrapGroupKey(creator, member.publicKey, chatId, groupKey)

        assertEquals(24, envelope.nonce.size)
        assertEquals(48, envelope.ciphertext.size)
        assertContentEquals(groupKey, DefaultChatCipher.unwrapGroupKey(member, creator.publicKey, chatId, envelope))
    }

    @Test
    fun selfWrap_opensForTheWrapper() {
        val envelope = DefaultChatCipher.wrapGroupKey(creator, creator.publicKey, chatId, groupKey)

        assertContentEquals(groupKey, DefaultChatCipher.unwrapGroupKey(creator, creator.publicKey, chatId, envelope))
    }

    @Test
    fun envelope_rejectsAnyOtherWrapperOrRecipient() {
        val envelope = DefaultChatCipher.wrapGroupKey(creator, member.publicKey, chatId, groupKey)

        assertFailsWith<ChatCipherException> {
            DefaultChatCipher.unwrapGroupKey(member, stranger.publicKey, chatId, envelope)
        }
        assertFailsWith<ChatCipherException> {
            DefaultChatCipher.unwrapGroupKey(stranger, creator.publicKey, chatId, envelope)
        }
    }

    @Test
    fun envelope_isBoundToTheChat() {
        val envelope = DefaultChatCipher.wrapGroupKey(creator, member.publicKey, chatId, groupKey)
        val otherChat = chatId.copyOf().also { it[0] = 0x7F }

        assertFailsWith<ChatCipherException> {
            DefaultChatCipher.unwrapGroupKey(member, creator.publicKey, otherChat, envelope)
        }
    }

    @Test
    fun wrappingKey_isNotTheDmChatKey() {
        // Same pair and chat, different label: a DM key must never open a group envelope.
        val nonce = ByteArray(24)
        val envelope = DefaultChatCipher.wrapGroupKey(creator, member.publicKey, chatId, groupKey, nonce)
        val dmKey = DefaultChatCipher.chatKey(creator, member.publicKey, chatId)
        val dmSealed = DefaultChatCipher.encrypt(groupKey, dmKey, creator.publicKey, member.publicKey, chatId, nonce)

        assertEquals(false, envelope.ciphertext.contentEquals(dmSealed.ciphertext))
    }

    @Test
    fun wrap_rejectsAShortKey() {
        assertFailsWith<ChatCipherException> {
            DefaultChatCipher.wrapGroupKey(creator, member.publicKey, chatId, ByteArray(16))
        }
    }

    @Test
    fun newGroupKey_isThirtyTwoFreshBytes() {
        val a = DefaultChatCipher.newGroupKey()
        val b = DefaultChatCipher.newGroupKey()

        assertEquals(32, a.size)
        assertEquals(false, a.contentEquals(b))
    }
}
