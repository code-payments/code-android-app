package com.getcode.chatcipher

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reproduces `payment_request_text.json` (canonical copy and generator in the orchestrator's
 * `test-vectors/`), which pins `EncryptedPaymentRequestText` against libsodium and an independent
 * HChaCha20 reference.
 */
class PaymentRequestTextVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("payment_request_text.json")).jsonObject
    private fun list(key: String) = root[key]!!.jsonArray.map { it.jsonObject }
    private val chatKey = root.hex("chatKey")
    private val chatId = root.hex("chatId")
    private val creatorId = root.hex("creatorId")

    @Test
    fun texts_encryptAndDecrypt() {
        val vectors = list("texts")
        assertEquals(4, vectors.size)
        for (v in vectors) {
            assertContentEquals(v.hex("aad"), root.string("label").encodeToByteArray() + chatId + creatorId, v.name())
            val payload = DefaultChatCipher.encryptPaymentRequestText(v.hex("plaintext"), chatKey, chatId, creatorId, v.hex("nonce"))
            assertContentEquals(v.hex("ciphertext"), payload.ciphertext, v.name())

            val plain = DefaultChatCipher.decryptPaymentRequestText(
                EncryptedPayload(v.hex("nonce"), v.hex("ciphertext")), chatKey, chatId, creatorId,
            )
            assertContentEquals(v.hex("plaintext"), plain, v.name())
            val fields = parseStrings(plain)
            assertEquals(v.string("title"), fields[1].orEmpty(), v.name())
            assertEquals(v.string("description"), fields[2].orEmpty(), v.name())
        }
    }

    @Test
    fun rejects_failToDecrypt() {
        val vectors = list("rejects")
        assertEquals(5, vectors.size)
        val label = root.string("label").encodeToByteArray()
        for (v in vectors) {
            val aad = v.hex("aad")
            val key = v.hex("chatKey")
            val payload = EncryptedPayload(v.hex("nonce"), v.hex("ciphertext"))
            // Exactly the aad the vector names.
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultChatCipher.open(payload.ciphertext, aad, payload.nonce, key)
            }
            // And through the public API, where the cipher builds the aad from chat and creator.
            if (aad.copyOfRange(0, label.size).contentEquals(label)) {
                val chat = aad.copyOfRange(label.size, label.size + 32)
                val creator = aad.copyOfRange(label.size + 32, aad.size)
                assertFailsWith<ChatCipherException>(v.name()) {
                    DefaultChatCipher.decryptPaymentRequestText(payload, key, chat, creator)
                }
            }
        }
    }

    @Test
    fun randomNonces_roundTripAndDiffer() {
        val v = list("texts")[1]
        val a = DefaultChatCipher.encryptPaymentRequestText(v.hex("plaintext"), chatKey, chatId, creatorId)
        val b = DefaultChatCipher.encryptPaymentRequestText(v.hex("plaintext"), chatKey, chatId, creatorId)
        assertTrue(!a.nonce.contentEquals(b.nonce))
        assertContentEquals(v.hex("plaintext"), DefaultChatCipher.decryptPaymentRequestText(a, chatKey, chatId, creatorId))
    }

    /** Length-delimited string fields of a proto message, by field number; enough for `PaymentRequestText`. */
    private fun parseStrings(bytes: ByteArray): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        var i = 0
        fun varint(): Int {
            var shift = 0
            var result = 0
            while (true) {
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (i < bytes.size) {
            val tag = varint()
            assertEquals(2, tag and 7, "only length-delimited fields expected")
            val len = varint()
            out[tag shr 3] = bytes.copyOfRange(i, i + len).decodeToString()
            i += len
        }
        return out
    }

    private fun JsonObject.name() = string("name")
    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.hex(key: String): ByteArray {
        val s = string(key)
        return ByteArray(s.length / 2) { i -> ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte() }
    }
}
