package com.flipcash.client

import com.flipcash.chat.ContentCodec
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.GrpcTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Checks this module against `flipcash_client.json`, which `ChatClientFixtureTest` in
 * `:services:flipcash` records from Android's protobuf-java code: request bytes must be equal, and
 * responses must decode to the values Android decodes them to, described in the same JSON shape.
 */
class FlipcashClientParityTest {
    private val root = Json.parseToJsonElement(readTestResource("flipcash_client.json")).jsonObject
    private val requests = root.obj("requests")
    private val owner = Ed25519.createKeyPair(root.str("ownerSeed").unhex())
    private val chatId = Id(root.str("chatId").unhex())

    // -- requests --

    @Test
    fun methodPathsMatchAndroid() {
        assertEquals(requests.str("streamEventsPath"), Requests.STREAM_EVENTS)
        requests.list("getReactors").forEach { assertEquals(it.str("path"), Requests.GET_REACTORS) }
    }

    @Test
    fun streamOpenBytesMatchAndroid() {
        val vectors = requests.list("streamEventsOpen")
        assertEquals(2, vectors.size)
        for (v in vectors) {
            assertContentEquals(v.hex("bytes"), Requests.streamEventsOpen(owner, v.instant()).encode(), v.toString())
        }
    }

    @Test
    fun pongBytesMatchAndroid() {
        val vectors = requests.list("pong")
        assertEquals(2, vectors.size)
        for (v in vectors) assertContentEquals(v.hex("bytes"), Requests.pong(v.instant()).encode(), v.toString())
    }

    @Test
    fun getReactorsBytesMatchAndroid() = runTest {
        val vectors = requests.list("getReactors")
        assertEquals(2, vectors.size)
        for (v in vectors) {
            val transport = FakeTransport(unaryResponse = byteArrayOf())
            MessagingClient(transport).getReactors(owner, chatId, v.long("messageId"), v.str("emoji"), v.options())
            assertEquals(listOf(Requests.GET_REACTORS), transport.methods, v.str("name"))
            assertContentEquals(v.hex("bytes"), transport.sent.single(), v.str("name"))
        }
    }

    // -- payment metadata --

    @Test
    fun paymentMetadataEncodesToAndroidBytes() {
        val vectors = root.list("paymentMetadata").filter { "decodeOnly" !in it }
        assertEquals(7, vectors.size)
        for (v in vectors) {
            val decoded = PaymentMetadataCodec.decode(v.hex("bytes"))!!
            assertContentEquals(v.hex("bytes"), PaymentMetadataCodec.encode(decoded), v.str("name"))
        }
    }

    @Test
    fun paymentMetadataDecodesLikeAndroid() {
        val vectors = root.list("paymentMetadata")
        assertEquals(9, vectors.size)
        for (v in vectors) {
            val expected = JsonObject(v - "name" - "bytes" - "decodeOnly")
            assertEquals(expected, describe(PaymentMetadataCodec.decode(v.hex("bytes"))), v.str("name"))
        }
    }

    // -- responses --

    @Test
    fun streamResponsesDecodeLikeAndroid() {
        val vectors = root.list("streamResponses")
        assertEquals(6, vectors.size)
        for (v in vectors) assertEquals(v.obj("expected"), describe(decodeStreamEvent(v.hex("bytes"))), v.str("name"))
    }

    @Test
    fun getReactorsResponsesDecodeLikeAndroid() {
        val vectors = root.list("getReactorsResponses")
        assertEquals(5, vectors.size)
        for (v in vectors) {
            val described = runCatching { decodeGetReactors(v.hex("bytes")) }.fold(
                onSuccess = { describe(it) },
                onFailure = {
                    buildJsonObject {
                        put("type", when (it) {
                            is GetReactorsException.Denied -> "denied"
                            is GetReactorsException.MessageNotFound -> "message-not-found"
                            is GetReactorsException.Unrecognized -> "unrecognized"
                            else -> throw it
                        })
                    }
                },
            )
            assertEquals(v.obj("expected"), described, v.str("name"))
        }
    }

    // -- the stream over a transport --

    @Test
    fun streamOpensAnswersPingAndEmitsUpdates() = runTest {
        val open = requests.list("streamEventsOpen").first()
        val pong = requests.list("pong").first()
        val responses = root.list("streamResponses")
        val transport = FakeTransport(
            streamResponses = listOf("ping", "events").map { name -> responses.first { it.str("name") == name }.hex("bytes") },
        )
        val times = ArrayDeque(listOf(open.instant(), pong.instant()))
        val clock = object : Clock {
            override fun now(): Instant = times.removeFirst()
        }

        val events = EventStreamClient(transport, clock).events(owner).take(2).toList()

        assertEquals(listOf(Requests.STREAM_EVENTS), transport.methods)
        assertEquals(2, transport.sent.size)
        assertContentEquals(open.hex("bytes"), transport.sent[0], "open")
        assertContentEquals(pong.hex("bytes"), transport.sent[1], "pong")
        assertIs<StreamEvent.Ping>(events[0])
        assertEquals(2, assertIs<StreamEvent.ChatUpdates>(events[1]).updates.size)
    }

    // -- describing values in the generator's shape --

    private fun describe(metadata: ChatPaymentMetadata?): JsonObject = buildJsonObject {
        when (metadata) {
            null -> put("kind", "none")
            is ChatPaymentMetadata.GroupPayment -> put("kind", "group")
            is ChatPaymentMetadata.ContactDmPayment -> {
                put("kind", "contactDm"); put("sourcePhone", metadata.sourcePhone); put("destinationPhone", metadata.destinationPhone)
            }
            is ChatPaymentMetadata.TipDmPayment -> {
                put("kind", "tipDm"); put("origin", metadata.origin.name); metadata.action?.let { put("action", it.name) }
            }
            is ChatPaymentMetadata.ReturnPayment -> { put("kind", "return"); put("messageId", metadata.messageId) }
            is ChatPaymentMetadata.RequestedPayment -> {
                put("kind", "requested"); put("paymentRequestId", metadata.paymentRequestId.bytes.hex())
            }
            is ChatPaymentMetadata.ReactionBoost -> {
                put("kind", "reactionBoost"); put("messageId", metadata.messageId); put("emoji", metadata.emoji)
            }
        }
    }

    private fun describe(event: StreamEvent?): JsonObject = buildJsonObject {
        when (event) {
            null -> put("type", "none")
            is StreamEvent.Ping -> {
                put("type", "ping"); put("serverTime", event.serverTime.describe()); put("pingDelaySeconds", event.pingDelaySeconds)
            }
            is StreamEvent.Error -> { put("type", "error"); put("code", event.code.name) }
            is StreamEvent.ChatUpdates -> {
                put("type", "updates")
                put("updates", buildJsonArray { event.updates.forEach { add(describe(it)) } })
            }
        }
    }

    private fun describe(page: ReactorsPage): JsonObject = buildJsonObject {
        put("type", "page")
        put("reactors", buildJsonArray { page.reactors.forEach { add(describe(it)) } })
        put("hasMore", page.hasMore)
        page.pagingToken?.let { put("pagingToken", it.bytes.hex()) }
    }

    private fun describe(update: ChatUpdate): JsonObject = buildJsonObject {
        put("chatId", update.chatId.bytes.hex())
        put("pointers", buildJsonArray {
            update.pointerUpdates.forEach { p ->
                add(buildJsonObject {
                    put("type", p.type.name); put("userId", p.userId.bytes.hex()); put("value", p.value); put("ts", p.timestamp.describe())
                })
            }
        })
        put("typing", buildJsonArray {
            update.typingNotifications.forEach { t ->
                add(buildJsonObject { put("userId", t.userId.bytes.hex()); put("state", t.state.name) })
            }
        })
        put("events", buildJsonArray {
            update.events.forEach { e ->
                add(buildJsonObject {
                    put("sequence", e.sequence); put("count", e.count); put("ts", e.ts.describe())
                    put("mutations", buildJsonArray {
                        e.mutations.forEach { m ->
                            add(buildJsonObject {
                                put("kind", when (m) {
                                    is ChatMutation.MessageSent -> "sent"
                                    is ChatMutation.MessageEdited -> "edited"
                                    is ChatMutation.MessageDeleted -> "deleted"
                                })
                                put("message", describe(m.message))
                            })
                        }
                    })
                })
            }
        })
        put("reactionUpdates", buildJsonArray {
            update.reactionUpdates.forEach { u ->
                add(buildJsonObject {
                    put("messageId", u.messageId); put("emoji", u.emoji); put("actor", u.actor.bytes.hex())
                    put("action", u.action.name); put("count", u.count); put("version", u.version)
                    put("reactedAt", u.reactedAt.describe()); put("boostCents", u.boostCents); put("actorBoostCents", u.actorBoostCents)
                })
            }
        })
    }

    private fun describe(message: ChatMessage): JsonObject = buildJsonObject {
        put("messageId", message.messageId)
        message.senderId?.let { put("senderId", it.bytes.hex()) }
        put("content", buildJsonArray { message.content.forEach { add(ContentCodec.encode(it).hex()) } })
        put("ts", message.timestamp.describe())
        put("unreadSeq", message.unreadSeq)
        message.lastEditedTs?.let { put("lastEditedTs", it.describe()) }
        put("eventSequence", message.eventSequence)
        message.reactions?.let { s ->
            put("reactions", buildJsonObject {
                put("messageId", s.messageId)
                put("reactions", buildJsonArray {
                    s.reactions.forEach { r ->
                        add(buildJsonObject {
                            put("emoji", r.emoji); put("count", r.count)
                            r.selfReactor?.let { put("selfReactor", describe(it)) }
                            put("sampleReactors", buildJsonArray { r.sampleReactors.forEach { add(describe(it)) } })
                            put("version", r.version); put("boostCents", r.boostCents)
                        })
                    }
                })
            })
        }
        put("redacted", message.redacted)
    }

    private fun describe(reactor: Reactor): JsonObject = buildJsonObject {
        put("userId", reactor.userId.bytes.hex()); put("reactedAt", reactor.reactedAt.describe())
        put("version", reactor.version); put("boostCents", reactor.boostCents)
    }

    private fun Instant.describe(): String = "$epochSeconds.${nanosecondsOfSecond.toString().padStart(9, '0')}"
}

/** Records what the client sends; answers unary calls with [unaryResponse]. */
private class FakeTransport(
    private val unaryResponse: ByteArray = byteArrayOf(),
    private val streamResponses: List<ByteArray> = emptyList(),
) : GrpcTransport {
    val methods = mutableListOf<String>()
    val sent = mutableListOf<ByteArray>()

    override suspend fun unary(method: String, request: ByteArray): ByteArray {
        methods += method
        sent += request
        return unaryResponse
    }

    /** Waits for one request before each response: the open before the first, a pong after a ping. */
    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> = channelFlow {
        methods += method
        val received = Channel<ByteArray>(Channel.UNLIMITED)
        launch { requests.collect { sent += it; received.send(it) } }
        for (response in streamResponses) {
            received.receive()
            send(response)
        }
        awaitClose()
    }

    override fun close() = Unit
}

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
private fun JsonObject.list(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }
private fun JsonObject.hex(key: String): ByteArray = str(key).unhex()
private fun JsonObject.instant(): Instant = Instant.fromEpochSeconds(long("seconds"), long("nanos"))
private fun JsonObject.options(): QueryOptions = QueryOptions(
    limit = long("limit").toInt(),
    descending = getValue("descending").jsonPrimitive.content.toBoolean(),
    token = get("token")?.let { Id(it.jsonPrimitive.content.unhex()) },
)
private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
