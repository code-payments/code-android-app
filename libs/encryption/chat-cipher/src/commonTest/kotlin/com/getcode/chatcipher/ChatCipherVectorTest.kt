package com.getcode.chatcipher

import com.getcode.ed25519kmp.Ed25519Kmp
import com.getcode.ed25519kmp.KeyPair
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** GATE: reproduces every vector in `chat_cipher.json` (canonical: test-vectors/chat_cipher.json). */
class ChatCipherVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("chat_cipher.json")).jsonObject
    private fun list(key: String) = root[key]!!.jsonArray.map { it.jsonObject }
    private fun rejects(key: String) = root["rejects"]!!.jsonObject[key]!!.jsonArray.map { it.jsonObject }

    @Test
    fun keyConversion() {
        val vectors = list("keyConversion")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            val pair = Ed25519Kmp.createKeyPair(v.hex("edSeed"))
            assertContentEquals(v.hex("edPublicKey"), pair.publicKey, v.name())
            assertContentEquals(v.hex("x25519PrivateKey"), DefaultChatCipher.ownToX25519(pair), v.name())
            assertContentEquals(v.hex("x25519PublicKey"), DefaultChatCipher.peerToX25519(pair.publicKey), v.name())
        }
    }

    @Test
    fun chatKey_isTheSameFromBothMembers() {
        val vectors = list("chatKey")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            val a = pair(v["memberA"]!!.jsonObject)
            val b = pair(v["memberB"]!!.jsonObject)
            val chatId = v.hex("chatId")
            assertContentEquals(v.hex("chatKey"), DefaultChatCipher.chatKey(a, b.publicKey, chatId), "${v.name()} A->B")
            assertContentEquals(v.hex("chatKey"), DefaultChatCipher.chatKey(b, a.publicKey, chatId), "${v.name()} B->A")
            val ss = DefaultChatCipher.sharedSecret(DefaultChatCipher.ownToX25519(a), DefaultChatCipher.peerToX25519(b.publicKey))
            assertContentEquals(v.hex("sharedSecret"), ss, v.name())
        }
    }

    @Test
    fun messages_encryptAndDecrypt() {
        val vectors = list("messages")
        assertTrue(vectors.size >= 7)
        for (v in vectors) {
            val key = v.hex("chatKey")
            val payload = DefaultChatCipher.encrypt(
                v.hex("content"), key, v.hex("senderPublicKey"), v.hex("recipientPublicKey"),
                v.hex("chatId"), v.hex("nonce"),
            )
            assertContentEquals(v.hex("ciphertext"), payload.ciphertext, v.name())
            val plain = DefaultChatCipher.decrypt(
                v.payload(), key, v.hex("senderPublicKey"), v.hex("recipientPublicKey"), v.hex("chatId"),
            )
            assertContentEquals(v.hex("content"), plain, v.name())
        }
    }

    @Test
    fun blobs_encryptAndDecrypt() {
        val vectors = list("blobs")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            val key = v.hex("chatKey")
            val blob = DefaultChatCipher.encryptBlob(
                v.hex("image"), key, v.hex("senderPublicKey"), v.hex("recipientPublicKey"),
                v.hex("chatId"), v.hex("blobId"), v.hex("nonce"),
            )
            assertContentEquals(v.hex("blob"), blob, v.name())
            val plain = DefaultChatCipher.decryptBlob(
                v.hex("blob"), key, v.hex("senderPublicKey"), v.hex("recipientPublicKey"),
                v.hex("chatId"), v.hex("blobId"),
            )
            assertContentEquals(v.hex("image"), plain, v.name())
        }
    }

    @Test
    fun randomNonces_roundTripAndDiffer() {
        val v = list("messages").first()
        val args = arrayOf(v.hex("chatKey"), v.hex("senderPublicKey"), v.hex("recipientPublicKey"), v.hex("chatId"))
        val a = DefaultChatCipher.encrypt(v.hex("content"), args[0], args[1], args[2], args[3])
        val b = DefaultChatCipher.encrypt(v.hex("content"), args[0], args[1], args[2], args[3])
        assertTrue(!a.nonce.contentEquals(b.nonce))
        assertContentEquals(v.hex("content"), DefaultChatCipher.decrypt(a, args[0], args[1], args[2], args[3]))
    }

    @Test
    fun rejects_invalidPeerKeys() {
        val vectors = rejects("invalidEdPublicKeys")
        assertTrue(vectors.size >= 4)
        for (v in vectors) {
            val own = Ed25519Kmp.createKeyPair(v.hex("ownSeed"))
            assertFailsWith<ChatCipherException>(v.name()) { DefaultChatCipher.chatKey(own, v.hex("edPublicKey"), v.hex("chatId")) }
        }
    }

    @Test
    fun rejects_zeroSharedSecret() {
        for (v in rejects("zeroSharedSecret")) {
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultChatCipher.sharedSecret(v.hex("x25519PrivateKey"), v.hex("peerX25519PublicKey"))
            }
        }
    }

    @Test
    fun rejects_messages() {
        for (key in listOf("decrypt", "decryptBlobAsMessage")) {
            val vectors = rejects(key)
            assertTrue(vectors.isNotEmpty())
            for (v in vectors) {
                assertFailsWith<ChatCipherException>("$key/${v.name()}") {
                    DefaultChatCipher.decrypt(
                        v.payload(), v.hex("chatKey"), v.hex("senderPublicKey"), v.hex("recipientPublicKey"),
                        v.hex("chatId"),
                    )
                }
            }
        }
    }

    @Test
    fun rejects_blobs() {
        val vectors = rejects("decryptBlob")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultChatCipher.decryptBlob(
                    v.hex("blob"), v.hex("chatKey"), v.hex("senderPublicKey"), v.hex("recipientPublicKey"),
                    v.hex("chatId"), v.hex("blobId"),
                )
            }
        }
    }

    @Test
    fun policy_shouldEncrypt() {
        val policy = root["policy"]!!.jsonObject
        assertContentEquals(policy.hex("flipcashUserId"), ChatEncryptionPolicy.FLIPCASH_USER_ID)
        val vectors = policy["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(vectors.size >= 4)
        for (v in vectors) {
            val actual = ChatEncryptionPolicy.shouldEncrypt(
                isDirectMessage = v.bool("isDirectMessage"),
                useE2ee = v.bool("useE2ee"),
                peerUserId = v.hex("peerUserId"),
            )
            assertEquals(v.bool("shouldEncrypt"), actual, v.name())
        }
    }

    @Test
    fun flipcashUserId_isACopy() {
        ChatEncryptionPolicy.FLIPCASH_USER_ID[0] = 0
        assertTrue(!ChatEncryptionPolicy.shouldEncrypt(true, true, root["policy"]!!.jsonObject.hex("flipcashUserId")))
    }

    private fun pair(o: JsonObject) = Ed25519Kmp.createKeyPair(o.hex("edSeed")).also {
        assertContentEquals(o.hex("edPublicKey"), it.publicKey)
    }

    private fun JsonObject.payload() = EncryptedPayload(hex("nonce"), hex("ciphertext"))
    private fun JsonObject.bool(key: String) = this[key]!!.jsonPrimitive.boolean
    private fun JsonObject.name() = this["name"]!!.jsonPrimitive.content
    private fun JsonObject.hex(key: String): ByteArray {
        val s = this[key]!!.jsonPrimitive.content
        return ByteArray(s.length / 2) { i -> ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte() }
    }
}
