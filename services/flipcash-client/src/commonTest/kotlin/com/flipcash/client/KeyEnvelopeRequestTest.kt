package com.flipcash.client

import com.flipcash.chat.KeyEnvelope
import com.getcode.ed25519kmp.Ed25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Set/GetKeyEnvelope requests in `chat_envelopes.json` were captured from Android's real
 * `ChatApi` (protobuf-java, real `authenticate()`); [KeyEnvelopeRequests] must build the same bytes.
 * Its Set/GetKeyEnvelope responses were built with protobuf-java and must decode to the same
 * values through [KeyEnvelopeResponses]. Building a SetKeyEnvelope request sends nothing. Its content vectors were encoded by Android's
 * mappers; `ContentCodec` writes Wire's order, which canonicalizes to the same bytes.
 */
class KeyEnvelopeRequestTest {

    private val root = Json.parseToJsonElement(readTestResource("chat_envelopes.json")).jsonObject
    private val owner = Ed25519.createKeyPair(root.text("ownerSeed").unhex())
    private val requests = root.getValue("requests").jsonObject
    private val chatId = requests.text("chatId").unhex()

    private fun list(key: String) = root.getValue(key).jsonArray.map { it.jsonObject }

    @Test
    fun contentCanonicalBytesMatchAndroid() {
        val failures = root.getValue("contents").jsonArray.map { it.jsonObject }.filter { it.text("direction") == "encode" }.mapNotNull { v ->
            val bytes = v.text("bytes")
            val reEncoded = canonicalContent(com.flipcash.chat.ContentCodec.encode(com.flipcash.chat.ContentCodec.decode(bytes.unhex()))).hex()
            if (reEncoded == bytes) null else v.text("name")
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun getKeyEnvelopeMatchesAndroid() {
        assertEquals(requests.text("getKeyEnvelope"), KeyEnvelopeRequests.getKeyEnvelopeRequest(owner, chatId).hex())
    }

    @Test
    fun setKeyEnvelopeMatchesAndroid() {
        val set = requests.getValue("setKeyEnvelope").jsonObject
        val w = root.getValue("keyEnvelopes").jsonArray.map { it.jsonObject }.first { it.text("name") == set.text("envelope") }
        val envelope = KeyEnvelope(w.getValue("scheme").jsonPrimitive.int, w.text("nonce").unhex(), w.text("ciphertext").unhex())
        assertEquals(set.text("request"), KeyEnvelopeRequests.setKeyEnvelopeRequest(owner, chatId, envelope).hex())
    }

    @Test
    fun getKeyEnvelopeResponsesDecodeLikeAndroid() {
        val vectors = list("getKeyEnvelopeResponses")
        assertEquals(9, vectors.size)
        for (v in vectors) {
            assertEquals(v.getValue("decoded").jsonObject, describe(KeyEnvelopeResponses.decodeGetKeyEnvelopeResponse(v.text("bytes").unhex())), v.text("name"))
        }
    }

    @Test
    fun setKeyEnvelopeResponsesDecodeLikeAndroid() {
        for (v in list("setKeyEnvelopeResponses")) {
            val expected = v.text("result").let { if (it == "UNRECOGNIZED") "UNKNOWN" else it }
            assertEquals(expected, KeyEnvelopeResponses.decodeSetKeyEnvelopeResponse(v.text("bytes").unhex()).name, v.text("name"))
        }
    }

    // -- descriptions, in ChatEnvelopeFixtureTest's shape --

    private fun describe(lookup: KeyEnvelopeLookup): JsonObject = buildJsonObject {
        when (lookup) {
            KeyEnvelopeLookup.Denied -> put("type", "denied")
            KeyEnvelopeLookup.NotFound -> put("type", "not-found")
            KeyEnvelopeLookup.NoEnvelope -> put("type", "no-envelope")
            is KeyEnvelopeLookup.Own -> {
                put("type", "own")
                put("scheme", lookup.envelope.scheme)
                put("nonce", lookup.envelope.nonce.hex()); put("ciphertext", lookup.envelope.ciphertext.hex())
                wrappers(lookup.wrappedBy, lookup.wrappedByPublicKey)
            }
            is KeyEnvelopeLookup.InviteOnly -> {
                val b = lookup.bundle
                put("type", "invite-only")
                put("inviteId", b.inviteId.hex())
                put("keyEnvelopeScheme", b.keyEnvelopeScheme)
                put("keyNonce", b.keyEnvelope.nonce.hex()); put("keyCiphertext", b.keyEnvelope.ciphertext.hex())
                put("keySignature", b.keyEnvelope.signature.hex())
                put("secretEnvelopeScheme", b.secretEnvelopeScheme)
                put("secretNonce", b.secretEnvelope.nonce.hex()); put("secretCiphertext", b.secretEnvelope.ciphertext.hex())
                wrappers(lookup.wrappedBy, lookup.wrappedByPublicKey)
            }
        }
    }

    private fun JsonObjectBuilder.wrappers(wrappedBy: ByteArray?, wrappedByPublicKey: ByteArray?) {
        wrappedBy?.let { put("wrappedBy", it.hex()) }
        wrappedByPublicKey?.let { put("wrappedByPublicKey", it.hex()) }
    }
}

private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
