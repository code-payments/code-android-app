package com.flipcash.chat

import com.getcode.ed25519kmp.Ed25519Kmp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Checks the Wire codec against `chat_envelopes.json`, which `ChatEnvelopeFixtureTest` in
 * `:services:flipcash` records from Android's protobuf-java code. Each value is described in the
 * same JSON shape that test uses, so a decode matches when the descriptions are equal.
 */
class ChatEnvelopeVectorTest {
    private val root = Json.parseToJsonElement(readTestResource("chat_envelopes.json")).jsonObject
    private fun list(key: String) = root.getValue(key).jsonArray.map { it.jsonObject }

    @Test
    fun contentDecodesLikeAndroid() {
        val vectors = list("contents")
        assertEquals(25, vectors.size)
        for (v in vectors) {
            val expected = if (v.str("name") == "media-unported") unsupported else v.obj("decoded")
            assertEquals(expected, describe(ContentCodec.decode(v.hex("bytes"))), v.str("name"))
        }
    }

    /**
     * Content is sealed, not signed, so its bytes only have to decode the same. Wire writes oneof
     * members after regular fields; `:services:flipcash-client`'s test checks the canonical bytes.
     */
    @Test
    fun contentEncodesBackToTheSameContent() {
        for (v in list("contents").filter { it.str("direction") == "encode" }) {
            val encoded = ContentCodec.encode(ContentCodec.decode(v.hex("bytes")))
            assertEquals(v.obj("decoded"), describe(ContentCodec.decode(encoded)), v.str("name"))
        }
    }

    @Test
    fun unknownEncryptionSchemeCannotBeEncoded() {
        val unknown = ContentCodec.decode(list("contents").first { it.str("name") == "encrypted-unknown-scheme" }.hex("bytes"))
        assertFailsWith<IllegalArgumentException> { ContentCodec.encode(unknown) }
    }

    @Test
    fun sealedContentOpens() {
        val crypto = ChatCrypto()
        val vectors = list("sealed")
        assertEquals(4, vectors.size)
        for (v in vectors) {
            val encrypted = assertIs<MessageContent.Encrypted>(ContentCodec.decode(v.hex("bytes")))
            val opened = when (v.str("kind")) {
                "dm" -> crypto.openDm(encrypted, v.hex("chatKey"), v.hex("senderPublicKey"), v.hex("recipientPublicKey"), v.hex("chatId"))
                else -> crypto.openGroup(encrypted, v.hex("groupKey"), v.hex("chatId"), v.hex("senderId"))
            }
            assertEquals(v.obj("opened"), describe(assertIs<OpenedContent.Plaintext>(opened, v.str("name")).content), v.str("name"))
        }
    }

    @Test
    fun sealedContentFailsWithTheWrongSender() {
        val v = list("sealed").first { it.str("kind") == "group" }
        val encrypted = assertIs<MessageContent.Encrypted>(ContentCodec.decode(v.hex("bytes")))
        val opened = ChatCrypto().openGroup(encrypted, v.hex("groupKey"), v.hex("chatId"), ByteArray(16))
        assertEquals(OpenedContent.Undecryptable(OpenedContent.Reason.Authentication), opened)
    }

    @Test
    fun keyEnvelopesMatchAndroidAndOpen() {
        val vectors = list("keyEnvelopes")
        assertEquals(3, vectors.size)
        for (v in vectors) {
            val envelope = KeyEnvelope(v.int("scheme"), v.hex("nonce"), v.hex("ciphertext"))
            assertEquals(envelope, KeyEnvelopeCodec.decode(v.hex("bytes")), v.str("name"))
            if (v.str("name") == "unknown-scheme") {
                assertFailsWith<IllegalArgumentException> { KeyEnvelopeCodec.encode(envelope) }
                continue
            }
            assertContentEquals(v.hex("bytes"), KeyEnvelopeCodec.encode(envelope), v.str("name"))
            val recipient = Ed25519Kmp.createKeyPair(v.hex("recipientSeed"))
            val opened = GroupKeys().open(recipient, v.hex("wrapperPublicKey"), v.hex("chatId"), envelope)
            assertContentEquals(v.hex("groupKey"), opened, v.str("name"))
        }
    }

    @Test
    fun getKeyEnvelopeResponsesDecodeLikeAndroid() {
        val vectors = list("getKeyEnvelopeResponses")
        assertEquals(9, vectors.size)
        for (v in vectors) {
            assertEquals(v.obj("decoded"), describe(KeyEnvelopeCodec.decodeGetKeyEnvelopeResponse(v.hex("bytes"))), v.str("name"))
        }
    }

    @Test
    fun setKeyEnvelopeResponsesDecodeLikeAndroid() {
        for (v in list("setKeyEnvelopeResponses")) {
            val expected = v.str("result").let { if (it == "UNRECOGNIZED") "UNKNOWN" else it }
            assertEquals(expected, KeyEnvelopeCodec.decodeSetKeyEnvelopeResponse(v.hex("bytes")).name, v.str("name"))
        }
    }

    // -- descriptions, in ChatEnvelopeFixtureTest's shape --

    private val unsupported = buildJsonObject { put("type", "unsupported") }

    private fun describe(content: MessageContent): JsonObject = buildJsonObject {
        when (content) {
            is MessageContent.Text -> {
                put("type", "text"); put("text", content.text)
                content.linkPreview?.let { p ->
                    put("linkPreview", buildJsonObject { put("url", p.url); put("title", p.title); put("description", p.description) })
                }
            }
            is MessageContent.Cash -> {
                put("type", "cash")
                put("intentId", content.intentId.hex())
                put("mint", content.mint.hex())
                put("quarks", content.quarks)
                put("verb", content.verb.name)
                content.receiver?.let { put("receiver", it.hex()) }
                put("multiReceiver", buildJsonArray {
                    content.multiReceiver.forEach { p ->
                        add(buildJsonObject { put("receiver", p.receiver.hex()); put("nativeAmount", p.nativeAmount); put("quarks", p.quarks) })
                    }
                })
                content.paymentRequestId?.let { put("paymentRequestId", it.hex()) }
                content.paymentReturn?.let { r ->
                    put("paymentReturn", buildJsonObject {
                        put("offer", r.offer)
                        r.returnOf?.let { put("returnOf", it) }
                    })
                }
            }
            is MessageContent.Reply -> {
                put("type", "reply")
                put("repliedMessageId", content.repliedMessageId)
                put("content", buildJsonArray { content.content.forEach { add(describe(it)) } })
            }
            is MessageContent.Widget.PaymentRequest -> { put("type", "widget-payment-request"); put("paymentRequestId", content.paymentRequestId.hex()) }
            is MessageContent.Widget.ShareProfile -> { put("type", "widget-share-profile"); put("username", content.username) }
            MessageContent.Widget.Unsupported -> put("type", "widget-unsupported")
            is MessageContent.System -> {
                put("type", "system")
                put("fallbackText", content.fallbackText)
                content.userJoined?.let { j ->
                    put("userJoined", buildJsonObject {
                        put("userId", j.userId.hex())
                        j.admittedBy?.let { put("admittedBy", it.hex()) }
                        j.invitedBy?.let { put("invitedBy", it.hex()) }
                    })
                }
            }
            is MessageContent.Deleted -> {
                put("type", "deleted")
                put("seconds", content.deletedAtEpochSeconds)
                put("nanos", content.deletedAtNanos)
                content.deletedBy?.let { put("deletedBy", it.hex()) }
            }
            is MessageContent.Encrypted -> {
                put("type", "encrypted"); put("scheme", content.scheme)
                put("nonce", content.nonce.hex()); put("ciphertext", content.ciphertext.hex())
            }
            MessageContent.Unsupported -> put("type", "unsupported")
        }
    }

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

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.int(key: String): Int = str(key).toInt()
private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
private fun JsonObject.hex(key: String): ByteArray = str(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
