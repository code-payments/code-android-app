package com.getcode.chatcipher

import com.getcode.ed25519kmp.Ed25519Kmp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Reproduces `invite.json`, which `gen_invite.py` in the orchestrator repo builds from libsodium and `cryptography`. */
class InviteVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("invite.json")).jsonObject
    private fun list(key: String) = root[key]!!.jsonArray.map { it.jsonObject }
    private fun rejects(key: String) = root["rejects"]!!.jsonObject[key]!!.jsonArray.map { it.jsonObject }

    @Test
    fun derivation() {
        val vectors = list("derivation")
        assertEquals(8, vectors.size)
        for (v in vectors) {
            val secret = v.hex("secret")
            assertContentEquals(v.hex("inviteToken"), DefaultInviteCipher.inviteToken(secret), v.name())
            assertContentEquals(v.hex("wrapKey"), DefaultInviteCipher.wrapKey(secret, v.hex("chatId")), v.name())
            assertContentEquals(v.hex("inviteId"), DefaultInviteCipher.inviteId(secret), v.name())
        }
    }

    @Test
    fun keyEnvelopes_sealAndOpen() {
        val vectors = list("keyEnvelopes")
        assertEquals(4, vectors.size)
        for (v in vectors) {
            val inviter = Ed25519Kmp.createKeyPair(v.hex("inviterSeed"))
            assertContentEquals(v.hex("inviterPublicKey"), inviter.publicKey, v.name())
            assertContentEquals(v.hex("inviteId"), DefaultInviteCipher.inviteId(v.hex("secret")), v.name())
            assertContentEquals(v.hex("wrapKey"), DefaultInviteCipher.wrapKey(v.hex("secret"), v.hex("chatId")), v.name())
            assertContentEquals(
                v.hex("signedMessage"),
                DefaultInviteCipher.signedMessage(v.hex("chatId"), v.hex("inviteId"), v.hex("nonce"), v.hex("ciphertext")),
                v.name(),
            )
            val sealed = DefaultInviteCipher.sealInviteKeyEnvelope(inviter, v.hex("secret"), v.hex("chatId"), v.hex("chatKey"), v.hex("nonce"))
            assertContentEquals(v.hex("ciphertext"), sealed.ciphertext, v.name())
            assertContentEquals(v.hex("signature"), sealed.signature, v.name())
            val chatKey = DefaultInviteCipher.openInviteKeyEnvelope(v.hex("secret"), v.hex("chatId"), inviter.publicKey, v.keyEnvelope())
            assertContentEquals(v.hex("chatKey"), chatKey, v.name())
        }
    }

    @Test
    fun secretEnvelopes_sealAndOpen() {
        val vectors = list("secretEnvelopes")
        assertEquals(4, vectors.size)
        for (v in vectors) {
            val owner = Ed25519Kmp.createKeyPair(v.hex("ownerSeed"))
            assertContentEquals(v.hex("ownerPublicKey"), owner.publicKey, v.name())
            assertContentEquals(v.hex("wrapKey"), DefaultInviteCipher.secretKey(owner, v.hex("chatId"), v.hex("inviteId")), v.name())
            val sealed = DefaultInviteCipher.sealInviteSecretEnvelope(owner, v.hex("secret"), v.hex("chatId"), v.hex("inviteId"), v.hex("nonce"))
            assertContentEquals(v.hex("ciphertext"), sealed.ciphertext, v.name())
            val secret = DefaultInviteCipher.openInviteSecretEnvelope(owner, v.hex("chatId"), v.hex("inviteId"), v.payload())
            assertContentEquals(v.hex("secret"), secret, v.name())
        }
    }

    @Test
    fun bundles_open() {
        val vectors = list("bundles")
        assertTrue(vectors.isNotEmpty())
        for (v in vectors) {
            val result = v.openBundle(v.hex("inviteId"))
            assertIs<InviteBundleResult.Opened>(result, v.name())
            assertContentEquals(v.hex("chatKey"), result.chatKey, v.name())
        }
    }

    @Test
    fun links_encode() {
        val vectors = list("links")
        assertEquals(4, vectors.size)
        for (v in vectors) {
            assertEquals(v.string("url"), InviteLink.url(v.hex("secret")), v.name())
            assertEquals(v.string("url"), "https://app.flipcash.com/chat/invite#" + v.string("fragment"), v.name())
            assertContentEquals(v.hex("secret"), InviteLink.parseFragment(v.string("fragment")), v.name())
        }
    }

    @Test
    fun fragmentParse() {
        val vectors = list("fragmentParse")
        assertEquals(13, vectors.size)
        for (v in vectors) {
            val parsed = InviteLink.parseFragment(v.string("fragment"))
            if (v["secret"] is JsonNull) assertNull(parsed, v.name()) else assertContentEquals(v.hex("secret"), parsed, v.name())
        }
    }

    @Test
    fun routes() {
        val vectors = list("routes")
        assertEquals(7, vectors.size)
        for (v in vectors) {
            val route = InviteLink.route(v.string("url"))
            when (v.string("route")) {
                "invite" -> {
                    assertIs<InviteLinkRoute.Invite>(route, v.name())
                    assertContentEquals(v.hex("secret"), route.secret, v.name())
                }
                "invalidInvite" -> assertEquals(InviteLinkRoute.InvalidInvite, route, v.name())
                "chat" -> assertEquals(InviteLinkRoute.Chat, route, v.name())
                "other" -> assertEquals(InviteLinkRoute.Other, route, v.name())
                else -> fail("${v.name()}: unknown route ${v.string("route")}")
            }
        }
    }

    @Test
    fun rejects_keyEnvelopes_failAtTheirStep() {
        val vectors = rejects("keyEnvelope")
        assertEquals(8, vectors.size)
        for (v in vectors) {
            val secret = v.hex("secret")
            val result = DefaultInviteCipher.openKeyEnvelope(
                secret, v.hex("chatId"), DefaultInviteCipher.inviteId(secret), v.hex("inviterPublicKey"), v.keyEnvelope(),
            )
            val expected = when (v.string("failsAt")) {
                "signature" -> InviteBundleFailure.Signature
                "open" -> InviteBundleFailure.KeyOpen
                else -> fail("${v.name()}: unknown step ${v.string("failsAt")}")
            }
            assertIs<InviteBundleResult.Rejected>(result, v.name())
            assertEquals(expected, result.failsAt, v.name())
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultInviteCipher.openInviteKeyEnvelope(secret, v.hex("chatId"), v.hex("inviterPublicKey"), v.keyEnvelope())
            }
        }
    }

    @Test
    fun rejects_secretEnvelopes() {
        val vectors = rejects("secretEnvelope")
        assertEquals(6, vectors.size)
        for (v in vectors) {
            assertEquals("open", v.string("failsAt"), v.name())
            val owner = Ed25519Kmp.createKeyPair(v.hex("ownerSeed"))
            assertContentEquals(v.hex("ownerPublicKey"), owner.publicKey, v.name())
            assertFailsWith<ChatCipherException>(v.name()) {
                DefaultInviteCipher.openInviteSecretEnvelope(owner, v.hex("chatId"), v.hex("inviteId"), v.payload())
            }
        }
    }

    @Test
    fun rejects_bundles_failAtTheirStep() {
        val vectors = rejects("bundles")
        assertEquals(3, vectors.size)
        // The reject cases carry no inviteId; every bundle in the fixture is for the same invite.
        val inviteId = list("bundles").single().hex("inviteId")
        for (v in vectors) {
            val result = v.openBundle(inviteId)
            val expected = InviteBundleFailure.entries.single { it.name.equals(v.string("failsAt"), ignoreCase = true) }
            assertIs<InviteBundleResult.Rejected>(result, v.name())
            assertEquals(expected, result.failsAt, v.name())
        }
    }

    @Test
    fun randomSeal_roundTripsAndDiffers() {
        val v = list("keyEnvelopes").first()
        val inviter = Ed25519Kmp.createKeyPair(v.hex("inviterSeed"))
        val a = DefaultInviteCipher.sealInviteKeyEnvelope(inviter, v.hex("secret"), v.hex("chatId"), v.hex("chatKey"))
        val b = DefaultInviteCipher.sealInviteKeyEnvelope(inviter, v.hex("secret"), v.hex("chatId"), v.hex("chatKey"))
        assertTrue(!a.nonce.contentEquals(b.nonce))
        assertContentEquals(v.hex("chatKey"), DefaultInviteCipher.openInviteKeyEnvelope(v.hex("secret"), v.hex("chatId"), inviter.publicKey, a))

        val secret = DefaultInviteCipher.newSecret()
        assertEquals(32, secret.size)
        assertContentEquals(secret, InviteLink.parseFragment(InviteLink.url(secret).substringAfter('#')))
    }

    private fun JsonObject.openBundle(inviteId: ByteArray): InviteBundleResult {
        val member = Ed25519Kmp.createKeyPair(hex("memberSeed"))
        assertContentEquals(hex("memberPublicKey"), member.publicKey, name())
        val key = this["keyEnvelope"]!!.jsonObject
        val secret = this["secretEnvelope"]!!.jsonObject
        return DefaultInviteCipher.openInviteKeyBundle(
            member = member,
            creatorPk = hex("creatorPublicKey"),
            wrappedByCreator = this["wrappedByIsCreator"]!!.jsonPrimitive.boolean,
            chatId = hex("chatId"),
            inviteId = inviteId,
            keyEnvelope = key.keyEnvelope(),
            secretEnvelope = secret.payload(),
        )
    }

    private fun JsonObject.keyEnvelope() = InviteKeyEnvelope(hex("nonce"), hex("ciphertext"), hex("signature"))
    private fun JsonObject.payload() = EncryptedPayload(hex("nonce"), hex("ciphertext"))
    private fun JsonObject.name() = this["name"]?.jsonPrimitive?.content ?: "bundle"
    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.hex(key: String): ByteArray {
        val s = this[key]!!.jsonPrimitive.content
        return ByteArray(s.length / 2) { i -> ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte() }
    }
}
