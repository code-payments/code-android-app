package com.flipcash.client

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.wire.common.v1.Auth
import com.getcode.ed25519kmp.Ed25519
import com.google.protobuf.ByteString
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Records `signed_flipcash_requests.json`, the reference `CanonicalSigningParityTest` checks on the
 * JVM, Node and the iOS simulator.
 *
 * For every flipcash2 message holding an `Auth` field, this writes a request with every field set
 * (in variants that between them set every member of every oneof, nested messages three levels deep), lets protobuf-java
 * (the lite runtime Android sends with) parse and re-serialize it, and signs those bytes with the
 * Ed25519 Android's `authenticate()` uses: the request without `auth`, so the signature is what
 * Android would send for it. protobuf-java writes fields in field-number order, as ocp-server's
 * ForceConsistentMarshal does.
 *
 * The seed is `ownerSeed` from `flipcash_client.json`, an existing public test vector.
 * UPDATE_SIGNED_FIXTURE=1 rewrites the file; otherwise the test fails if it is stale.
 */
class SignedRequestFixtureTest {

    @Test
    fun signedRequestsMatchFixture() {
        val clientFixture = readResource("flipcash_client.json")
        val seed = clientFixture.getValue("ownerSeed").jsonPrimitive.content
        val owner = Ed25519.createKeyPair(seed.unhex())
        val schema = WireSchemaReader.read(Auth::class.java, WIRE_PREFIX)
        val wireClasses = wireClassesByProtoName()
        val liteClasses = liteClassesByPath()

        val entries = schema.signedMessages().flatMap { name ->
            val wireClass = wireClasses.getValue(name)
            val authTag = wireClass.wireFields().single { it.adapter == AUTH_ADAPTER }.tag
            // Variant k sets the k-th member (by tag) of every oneof, so each member is set in some variant.
            val variants = (0 until MAX_ONEOF_MEMBERS)
                .map { k -> RequestFiller(oneofMember = k).fill(wireClass, depth = 0, skipTag = authTag) }
                .distinctBy { it.hex() }
            variants.mapIndexed { k, raw -> (if (k == 0) name else "$name#oneofMember$k") to raw }
                .map { (key, raw) -> key to entry(name, wireClass, authTag, raw, liteClasses, owner) }
        }
        val differing = entries.filter { (_, e) -> e.getValue("wireDefaultOrderDiffers").jsonPrimitive.content == "true" }.map { it.first.substringBefore('#') }.distinct()
        val sentDiffering = entries.filter { (_, e) -> e.getValue("wireDefaultSignedOrderDiffers").jsonPrimitive.content == "true" }.map { it.first.substringBefore('#') }.distinct()
        println("signed-fixture sent request differs under Wire order for ${sentDiffering.size}: $sentDiffering")
        println("signed-fixture types: ${entries.size}; Wire default order differs for ${differing.size}: $differing")
        val json = Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "ownerSeed" to JsonPrimitive(seed),
                    "wireDefaultOrderDiffers" to JsonArray(differing.map(::JsonPrimitive)),
                    "wireDefaultSentOrderDiffers" to JsonArray(sentDiffering.map(::JsonPrimitive)),
                    "requests" to JsonObject(entries.toMap()),
                ),
            ),
        ) + "\n"
        val file = File(checkNotNull(System.getProperty("signedFixtureFile")))
        if (System.getenv("UPDATE_SIGNED_FIXTURE") == "1") file.writeText(json)
        assertEquals(json, file.readText(), "signed_flipcash_requests.json is stale; re-run with UPDATE_SIGNED_FIXTURE=1")
    }

    private fun entry(
        name: String,
        wireClass: Class<*>,
        authTag: Int,
        raw: ByteArray,
        liteClasses: Map<String, Class<*>>,
        owner: Ed25519.KeyPair,
    ): JsonObject {
        val lite = liteClasses.getValue(wireClass.name.removePrefix(WIRE_PACKAGE))
        val unsigned = lite.reserialize(raw)
        val signature = Ed25519.sign(unsigned, owner)
        val auth = Common.Auth.newBuilder()
            .setKeyPair(
                Common.Auth.KeyPair.newBuilder()
                    .setPubKey(Common.PublicKey.newBuilder().setValue(ByteString.copyFrom(owner.publicKeyBytes)))
                    .setSignature(Common.Signature.newBuilder().setValue(ByteString.copyFrom(signature))),
            )
            .build().toByteArray()
        val signed = lite.reserialize(unsigned + lengthDelimited(authTag, auth))
        val adapter = wireClass.getField("ADAPTER").get(null) as ProtoAdapter<Any>
        val wireDefault = adapter.encode(adapter.decode(unsigned))
        return JsonObject(
            mapOf(
                "authTag" to JsonPrimitive(authTag),
                "unsigned" to JsonPrimitive(unsigned.hex()),
                "signature" to JsonPrimitive(signature.hex()),
                "signed" to JsonPrimitive(signed.hex()),
                "wireDefaultOrderDiffers" to JsonPrimitive(!wireDefault.contentEquals(unsigned)),
                "wireDefaultSignedOrderDiffers" to JsonPrimitive(!adapter.encode(adapter.decode(signed)).contentEquals(signed)),
            ),
        )
    }

    /** Writes a message's fields straight from its `@WireField` annotations, in field-number order. */
    private class RequestFiller(private val oneofMember: Int) {
        fun fill(cls: Class<*>, depth: Int, skipTag: Int? = null): ByteArray {
            val fields = cls.wireFields().filter { it.tag != skipTag }
            val oneofPicks = fields.filter { it.oneofName.isNotEmpty() }.groupBy { it.oneofName }.values.map { group -> group.sortedBy { it.tag }.let { it[minOf(oneofMember, it.lastIndex)] }.tag }.toSet()
            val chosen = fields.filter { it.oneofName.isEmpty() || it.tag in oneofPicks }.sortedBy { it.tag }
            val out = ByteArrayOutputStream()
            for (field in chosen) {
                if (field.keyAdapter.isNotEmpty()) {
                    val value = valueBytes(field.adapter, depth) ?: continue
                    val entry = valueBytes(field.keyAdapter, depth)!! .let { key -> key.withTag(1, field.keyAdapter) } + value.withTag(2, field.adapter)
                    out.write(lengthDelimited(field.tag, entry))
                    continue
                }
                val value = valueBytes(field.adapter, depth) ?: continue
                val repeats = if (field.label == WireField.Label.REPEATED || field.label == WireField.Label.PACKED) 2 else 1
                repeat(repeats) { out.write(value.withTag(field.tag, field.adapter)) }
            }
            return out.toByteArray()
        }

        /** The encoded value (without tag), or null to leave a too-deep message unset. */
        private fun valueBytes(adapter: String, depth: Int): ByteArray? {
            val (owner, member) = adapter.split('#')
            if (owner == "com.squareup.wire.ProtoAdapter") return scalar(member)
            val cls = Class.forName(owner)
            if (WireEnum::class.java.isAssignableFrom(cls)) {
                val value = cls.enumConstants.map { (it as WireEnum).value }.filter { it != 0 }.minOrNull() ?: 0
                return varint(value.toLong())
            }
            if (depth >= MAX_DEPTH) return null
            return fill(cls, depth + 1)
        }

        private fun scalar(member: String): ByteArray = when (member) {
            "BOOL" -> varint(1)
            "INT32", "UINT32", "INT64", "UINT64" -> varint(150)
            "SINT32", "SINT64" -> varint(299) // zigzag(-150)
            "FIXED32", "SFIXED32", "FLOAT" -> byteArrayOf(1, 2, 3, 4)
            "FIXED64", "SFIXED64", "DOUBLE" -> byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            "STRING" -> "value".encodeToByteArray()
            "BYTES" -> byteArrayOf(0x0a, 0x0b, 0x0c)
            "INSTANT", "DURATION" -> varint(1_700_000_000).withTag(1, "ProtoAdapter#INT64") + varint(5).withTag(2, "ProtoAdapter#INT32")
            else -> error("Unsupported adapter $member")
        }

        private fun ByteArray.withTag(tag: Int, adapter: String): ByteArray {
            val member = adapter.substringAfter('#')
            val wireType = when {
                !adapter.startsWith("com.squareup.wire.ProtoAdapter") && runCatching { WireEnum::class.java.isAssignableFrom(Class.forName(adapter.substringBefore('#'))) }.getOrDefault(false) -> 0
                !adapter.startsWith("com.squareup.wire.ProtoAdapter") && !adapter.startsWith("ProtoAdapter") -> 2
                member in setOf("BOOL", "INT32", "UINT32", "INT64", "UINT64", "SINT32", "SINT64") -> 0
                member in setOf("FIXED32", "SFIXED32", "FLOAT") -> 5
                member in setOf("FIXED64", "SFIXED64", "DOUBLE") -> 1
                else -> 2
            }
            val key = varint(((tag shl 3) or wireType).toLong())
            return if (wireType == 2) key + varint(size.toLong()) + this else key + this
        }
    }

    private fun Class<*>.reserialize(bytes: ByteArray): ByteArray {
        val message = getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes) as com.google.protobuf.MessageLite
        return message.toByteArray()
    }

    private fun wireClassesByProtoName(): Map<String, Class<*>> {
        val jar = File(Auth::class.java.protectionDomain.codeSource.location.toURI())
        return ZipFile(jar).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith(WIRE_PREFIX) && it.endsWith(".class") }
                .map { Class.forName(it.removeSuffix(".class").replace('/', '.'), false, javaClass.classLoader) }
                .filter { Message::class.java.isAssignableFrom(it) && !java.lang.reflect.Modifier.isAbstract(it.modifiers) }
                .associateBy { (it.getField("ADAPTER").get(null) as ProtoAdapter<*>).typeUrl!!.substringAfterLast('/') }
        }
    }

    /** protobuf-java classes keyed by "messaging.v1.GetMessagesRequest$Nested", dropping the outer class. */
    private fun liteClassesByPath(): Map<String, Class<*>> {
        val jar = File(Common::class.java.protectionDomain.codeSource.location.toURI())
        return ZipFile(jar).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith(LITE_PREFIX) && it.endsWith(".class") && '$' in it }
                .map { Class.forName(it.removeSuffix(".class").replace('/', '.'), false, javaClass.classLoader) }
                .filter { com.google.protobuf.GeneratedMessageLite::class.java.isAssignableFrom(it) }
                .associateBy { cls ->
                    val pkg = cls.name.removePrefix(LITE_PACKAGE).substringBeforeLast('.')
                    "$pkg." + cls.name.substringAfterLast('.').substringAfter('$')
                }
        }
    }

    private companion object {
        const val WIRE_PREFIX = "com/codeinc/flipcash/wire/"
        const val WIRE_PACKAGE = "com.codeinc.flipcash.wire."
        const val LITE_PREFIX = "com/codeinc/flipcash/gen/"
        const val LITE_PACKAGE = "com.codeinc.flipcash.gen."
        const val AUTH_ADAPTER = "com.codeinc.flipcash.wire.common.v1.Auth#ADAPTER"
        const val MAX_DEPTH = 3
        const val MAX_ONEOF_MEMBERS = 64
    }
}

private fun Class<*>.wireFields(): List<WireField> = declaredFields.mapNotNull { it.getAnnotation(WireField::class.java) }

private fun lengthDelimited(tag: Int, payload: ByteArray): ByteArray =
    varint(((tag shl 3) or 2).toLong()) + varint(payload.size.toLong()) + payload

private fun varint(value: Long): ByteArray {
    val out = ByteArrayOutputStream()
    var v = value
    while (true) {
        if (v and 0x7fL.inv() == 0L) { out.write(v.toInt()); break }
        out.write(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
    return out.toByteArray()
}

private fun readResource(name: String): JsonObject = Json.parseToJsonElement(
    checkNotNull(SignedRequestFixtureTest::class.java.classLoader.getResource(name)).readText(),
).jsonObject

private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
