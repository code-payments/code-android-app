package com.getcode.chatcipher

import com.getcode.ed25519kmp.Ed25519Kmp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reproduces `group_cipher.json`, which `scripts/gen_group_cipher.py` builds from libsodium directly. */
class GroupCipherVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("group_cipher.json")).jsonObject
    private fun list(key: String) = root[key]!!.jsonArray.map { it.jsonObject }

    @Test
    fun messages_encryptAndDecrypt() {
        val vectors = list("messages")
        assertTrue(vectors.size >= 4)
        for (v in vectors) {
            val payload = DefaultChatCipher.encryptGroup(
                v.hex("content"), v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"), v.hex("nonce"),
            )
            assertContentEquals(v.hex("ciphertext"), payload.ciphertext, v.name())
            val plain = DefaultChatCipher.decryptGroup(v.payload(), v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"))
            assertContentEquals(v.hex("content"), plain, v.name())
        }
    }

    @Test
    fun blobs_encryptAndDecrypt() {
        val vectors = list("blobs")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            val blob = DefaultChatCipher.encryptGroupBlob(
                v.hex("image"), v.hex("groupKey"), v.hex("chatId"), v.hex("blobId"), v.hex("senderId"), v.hex("nonce"),
            )
            assertContentEquals(v.hex("blob"), blob, v.name())
            val plain = DefaultChatCipher.decryptGroupBlob(
                v.hex("blob"), v.hex("groupKey"), v.hex("chatId"), v.hex("blobId"), v.hex("senderId"),
            )
            assertContentEquals(v.hex("image"), plain, v.name())
        }
    }

    @Test
    fun wraps_matchAndOpen() {
        val vectors = list("wraps")
        assertTrue(vectors.size >= 2)
        for (v in vectors) {
            val wrapper = Ed25519Kmp.createKeyPair(v.hex("wrapperSeed"))
            val recipient = Ed25519Kmp.createKeyPair(v.hex("recipientSeed"))
            assertContentEquals(v.hex("wrapperPublicKey"), wrapper.publicKey, v.name())
            val envelope = DefaultChatCipher.wrapGroupKey(wrapper, recipient.publicKey, v.hex("chatId"), v.hex("groupKey"), v.hex("nonce"))
            assertContentEquals(v.hex("ciphertext"), envelope.ciphertext, v.name())
            val key = DefaultChatCipher.unwrapGroupKey(recipient, wrapper.publicKey, v.hex("chatId"), v.payload())
            assertContentEquals(v.hex("groupKey"), key, v.name())
        }
    }

    @Test
    fun rejects_messages() {
        val vectors = root["rejects"]!!.jsonObject["decryptGroup"]!!.jsonArray.map { it.jsonObject }
        assertTrue(vectors.size >= 5)
        for (v in vectors) {
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultChatCipher.decryptGroup(v.payload(), v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"))
            }
        }
    }

    @Test
    fun randomNonces_roundTripAndDiffer() {
        val v = list("messages")[1]
        val a = DefaultChatCipher.encryptGroup(v.hex("content"), v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"))
        val b = DefaultChatCipher.encryptGroup(v.hex("content"), v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"))
        assertTrue(!a.nonce.contentEquals(b.nonce))
        assertContentEquals(v.hex("content"), DefaultChatCipher.decryptGroup(a, v.hex("groupKey"), v.hex("chatId"), v.hex("senderId")))
    }

    private fun JsonObject.payload() = EncryptedPayload(hex("nonce"), hex("ciphertext"))
    private fun JsonObject.name() = this["name"]!!.jsonPrimitive.content
    private fun JsonObject.hex(key: String): ByteArray {
        val s = this[key]!!.jsonPrimitive.content
        return ByteArray(s.length / 2) { i -> ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte() }
    }
}
