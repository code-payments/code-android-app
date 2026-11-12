package com.flipcash.client

import com.codeinc.flipcash.wire.common.v1.Auth
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.canonicalize
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every flipcash2 request holding an `Auth` field, signed the way this module signs: the canonical
 * bytes and the deterministic Ed25519 signature must equal what protobuf-java gives for the same
 * request. `signed_flipcash_requests.json` comes from `SignedRequestFixtureTest` on the JVM, which
 * fills each request (in variants that set every oneof member) and re-serializes it with the
 * protobuf-java runtime Android sends with. Runs on the JVM, Node and the iOS simulator.
 */
class CanonicalSigningParityTest {

    private val fixture = Json.parseToJsonElement(readTestResource("signed_flipcash_requests.json")).jsonObject
    private val owner = Ed25519.createKeyPair(fixture.text("ownerSeed").unhex())
    private val requests = fixture.getValue("requests").jsonObject

    @Test
    fun everySignedTypeIsCovered() {
        assertEquals(signedMessageAdapters.keys, requests.keys.map { it.substringBefore('#') }.toSet())
    }

    @Test
    fun canonicalBytesAndSignaturesMatchProtobufJava() {
        val failures = requests.mapNotNull { (key, value) ->
            val entry = value.jsonObject
            val adapter = adapterFor(key)
            val unsigned = adapter.decode(entry.text("unsigned").unhex())
            val auth = authFor(unsigned, owner)
            val signed = adapter.decode(entry.text("signed").unhex())
            when {
                unsigned.encodeFlipcash().hex() != entry.text("unsigned") -> "$key: unsigned bytes"
                auth.key_pair!!.signature!!.value_.hex() != entry.text("signature") -> "$key: signature"
                signed.encodeFlipcash().hex() != entry.text("signed") -> "$key: signed request bytes"
                else -> null
            }
        }
        assertEquals(emptyList(), failures)
    }

    /** The server re-marshals what it receives, in whatever order, without `auth`, then verifies. */
    @Test
    fun serverStyleReMarshalVerifies() {
        val failures = requests.mapNotNull { (key, value) ->
            val entry = value.jsonObject
            val received = adapterFor(key).encode(adapterFor(key).decode(entry.text("signed").unhex())) // Wire's own order
            val (withoutAuth, auth) = splitAuth(received, entry.getValue("authTag").jsonPrimitive.int)
            val reMarshalled = canonicalize(withoutAuth, key.substringBefore('#'), FlipcashCanonicalSchema)
            val keyPair = auth.key_pair!!
            if (Ed25519.verify(keyPair.signature!!.value_.toByteArray(), reMarshalled, keyPair.pub_key!!.value_.toByteArray())) null else key
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun tamperedRequestIsRejected() {
        val accepted = requests.mapNotNull { (key, value) ->
            val entry = value.jsonObject
            val (withoutAuth, auth) = splitAuth(entry.text("signed").unhex(), entry.getValue("authTag").jsonPrimitive.int)
            if (withoutAuth.isEmpty()) return@mapNotNull null
            val tampered = withoutAuth.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
            val keyPair = auth.key_pair!!
            if (Ed25519.verify(keyPair.signature!!.value_.toByteArray(), tampered, keyPair.pub_key!!.value_.toByteArray())) key else null
        }
        assertEquals(emptyList(), accepted)
        val signedCount = requests.values.count { it.jsonObject.text("unsigned").isNotEmpty() }
        assertTrue(signedCount > 100, "expected the tamper check to cover every non-empty request, got $signedCount")
    }

    /** Wire's own `encode()` must still differ where the fixture says, or this gate tests nothing. */
    @Test
    fun wireDefaultOrderDiffersWhereRecorded() {
        fun differing(field: String) = requests.filter { (key, value) ->
            val adapter = adapterFor(key)
            val bytes = value.jsonObject.text(field)
            adapter.encode(adapter.decode(bytes.unhex())).hex() != bytes
        }.keys.map { it.substringBefore('#') }.toSet()
        fun recorded(name: String) = fixture.getValue(name).jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(recorded("wireDefaultOrderDiffers"), differing("unsigned"))
        assertEquals(recorded("wireDefaultSentOrderDiffers"), differing("signed"))
        assertFalse(differing("unsigned").isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private fun adapterFor(key: String): ProtoAdapter<Message<*, *>> =
        signedMessageAdapters.getValue(key.substringBefore('#')) as ProtoAdapter<Message<*, *>>

    /** The request's top-level fields without [authTag], in their original order, and the `Auth`. */
    private fun splitAuth(bytes: ByteArray, authTag: Int): Pair<ByteArray, Auth> {
        val reader = ProtoReader(Buffer().write(bytes))
        val out = Buffer()
        val writer = ProtoWriter(out)
        var auth: Auth? = null
        val token = reader.beginMessage()
        while (true) {
            val tag = reader.nextTag()
            if (tag == -1) break
            val encoding = reader.peekFieldEncoding()!!
            if (tag == authTag) {
                auth = Auth.ADAPTER.decode(reader)
            } else {
                val value = encoding.rawProtoAdapter().decode(reader)
                @Suppress("UNCHECKED_CAST")
                (encoding.rawProtoAdapter() as ProtoAdapter<Any>).encodeWithTag(writer, tag, value)
            }
        }
        reader.endMessageAndGetUnknownFields(token)
        return out.readByteArray() to checkNotNull(auth)
    }
}

private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
