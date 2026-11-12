package com.flipcash.client

import com.flipcash.chat.KeyEnvelope
import com.getcode.ed25519kmp.Ed25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Set/GetKeyEnvelope requests in `chat_envelopes.json` were captured from Android's real
 * `ChatApi` (protobuf-java, real `authenticate()`); [KeyEnvelopeRequests] must build the same bytes.
 * Building a SetKeyEnvelope request sends nothing. Its content vectors were encoded by Android's
 * mappers; `ContentCodec` writes Wire's order, which canonicalizes to the same bytes.
 */
class KeyEnvelopeRequestTest {

    private val root = Json.parseToJsonElement(readTestResource("chat_envelopes.json")).jsonObject
    private val owner = Ed25519.createKeyPair(root.text("ownerSeed").unhex())
    private val requests = root.getValue("requests").jsonObject
    private val chatId = requests.text("chatId").unhex()

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
}

private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
