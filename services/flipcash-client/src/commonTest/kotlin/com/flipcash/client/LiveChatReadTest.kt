package com.flipcash.client

import com.codeinc.flipcash.wire.chat.v1.ChatType
import com.codeinc.flipcash.wire.chat.v1.GetDmChatFeedRequest
import com.codeinc.flipcash.wire.chat.v1.GetDmChatFeedResponse
import com.codeinc.flipcash.wire.chat.v1.GetGroupChatFeedRequest
import com.codeinc.flipcash.wire.chat.v1.GetGroupChatFeedResponse
import com.codeinc.flipcash.wire.chat.v1.Metadata
import com.codeinc.flipcash.wire.events.v1.StreamEventsResponse
import com.codeinc.flipcash.wire.messaging.v1.GetReactorsResponse
import com.flipcash.chat.ContentCodec
import com.flipcash.chat.MessageContent
import com.getcode.crypt.DerivePath
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.transport.GrpcTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Read-only calls from this module to the production Flipcash server, as a test account: the chat feeds, the event stream (open, answer pings, close) and GetReactors.
 *
 * Skipped unless `FLIPCASH_LIVE=1` and `FLIPCASH_TEST_ACCESS_KEY` holds the account's login entropy
 * (Base58). Prints counts and kinds only: never the key, ids or content.
 *
 * With `FLIPCASH_LIVE_DUMP` set to a directory, each response is written there as received, beside
 * its Wire re-encoding and this module's decode, for `LiveResponseParityTest` in :services:flipcash
 * to compare against protobuf-java. Point it outside the repo: the bytes are the account's data.
 */
class LiveChatReadTest {

    @Test
    fun readOnlyChatCalls() = runTest(timeout = 120.seconds) {
        val accessKey = liveEnv("FLIPCASH_TEST_ACCESS_KEY")
        if (liveEnv("FLIPCASH_LIVE") != "1" || accessKey.isNullOrBlank()) return@runTest
        val inner = liveTransport(FLIPCASH_HOST) ?: run {
            println("[$livePlatform] no transport on this target; skipped")
            return@runTest
        }
        val owner = MnemonicPhrase.fromEntropyB58(accessKey.trim()).getSolanaKeyPair(DerivePath.primary)
        val dump = Dump(liveEnv("FLIPCASH_LIVE_DUMP"), livePlatform)
        val transport = RecordingTransport(inner)

        // Real network waits: leave runTest's virtual clock, or a timeout would fire at once.
        withContext(Dispatchers.Default) {
            try {
                // 1. Chat feeds: the chats the account can see, each with its last message.
                val dmFeed = GetDmChatFeedResponse.ADAPTER.decode(
                    transport.unary(GET_DM_CHAT_FEED, dmFeedRequest(owner).encode()),
                )
                assertEquals(GetDmChatFeedResponse.Result.OK, dmFeed.result, "GetDmChatFeed")
                val groupFeed = GetGroupChatFeedResponse.ADAPTER.decode(
                    transport.unary(GET_GROUP_CHAT_FEED, groupFeedRequest(owner).encode()),
                )
                assertEquals(GetGroupChatFeedResponse.Result.OK, groupFeed.result, "GetGroupChatFeed")
                val chats = dmFeed.chats + groupFeed.chats
                println("[$livePlatform] feeds: ${dmFeed.chats.size} DM chats, ${groupFeed.chats.size} group chats")
                assertTrue(chats.isNotEmpty(), "the test account sees no chats")
                reportContent(chats)

                // 2. GetReactors on a visible message: one with a reaction if a feed shows one.
                val reacted = chats.firstNotNullOfOrNull { chat ->
                    val message = chat.last_message ?: return@firstNotNullOfOrNull null
                    val reaction = message.reactions?.reactions?.firstOrNull { it.count > 0 }
                        ?: return@firstNotNullOfOrNull null
                    Triple(chat, message, reaction.emoji?.value_.orEmpty())
                }
                val (chat, message, emoji) = reacted
                    ?: chats.first { it.last_message != null }.let { Triple(it, it.last_message!!, "👍") }
                val page = MessagingClient(transport).getReactors(
                    owner = owner,
                    chatId = chat.chat_id?.value_.toId(),
                    messageId = message.message_id!!.value_,
                    emoji = emoji,
                )
                println(
                    "[$livePlatform] GetReactors on a ${if (reacted != null) "reacted" else "plain"} message: " +
                        "OK, ${page.reactors.size} reactors, hasMore=${page.hasMore}",
                )
                if (reacted != null) assertTrue(page.reactors.isNotEmpty(), "a reacted message returned no reactors")

                // 3. The event stream: open, answer pings, take what arrives, then close.
                var pings = 0
                var batches = 0
                var updates = 0
                val outcome = withTimeoutOrNull(STREAM_WINDOW) {
                    EventStreamClient(transport).events(owner)
                        .onEach { event ->
                            when (event) {
                                is StreamEvent.Ping -> pings++
                                is StreamEvent.ChatUpdates -> { batches++; updates += event.updates.size }
                                is StreamEvent.Error -> fail("stream error ${event.code}")
                            }
                        }
                        // Two pings means the server kept the stream open after our first pong.
                        .takeWhile { pings < 2 }
                        .collect {}
                    "closed by us after a second ping"
                } ?: "idle until the ${STREAM_WINDOW.inWholeSeconds}s window ended"
                println("[$livePlatform] stream: $pings pings answered, $batches event batches ($updates chat updates); $outcome")
                assertTrue(pings >= 1, "no ping within ${STREAM_WINDOW.inWholeSeconds}s")

                // 4. Group payment metadata: no read call returns AppMetadata; it rides on
                // SubmitIntent only. Say so rather than pretend to have decoded one.
                println("[$livePlatform] payment metadata: none present (no read RPC returns AppMetadata)")
            } finally {
                inner.close()
            }
        }

        transport.responses.forEachIndexed { index, (method, bytes) -> dump.write(index, method, bytes) }
        println("[$livePlatform] ${transport.responses.size} responses${if (dump.enabled) " written to FLIPCASH_LIVE_DUMP" else ""}")
    }

    /** Counts the kinds of the feeds' last messages; the content itself is not printed. */
    private fun reportContent(chats: List<Metadata>) {
        val kinds = chats.mapNotNull { it.last_message }
            .flatMap { it.content }
            .map { ContentCodec.decode(it.encode())::class.simpleName ?: "?" }
            .groupingBy { it }.eachCount()
        val cash = chats.mapNotNull { it.last_message }.flatMap { it.content }
            .count { ContentCodec.decode(it.encode()) is MessageContent.Cash }
        println("[$livePlatform] last-message content kinds: $kinds ($cash cash)")
    }

    private fun dmFeedRequest(owner: com.getcode.ed25519kmp.Ed25519.KeyPair): GetDmChatFeedRequest {
        val unsigned = GetDmChatFeedRequest(query_options = QueryOptions(limit = FEED_PAGE).toWire(), dm_chat_type = ChatType.DM)
        return unsigned.copy(auth = authFor(unsigned.encode(), owner))
    }

    private fun groupFeedRequest(owner: com.getcode.ed25519kmp.Ed25519.KeyPair): GetGroupChatFeedRequest {
        val unsigned = GetGroupChatFeedRequest(query_options = QueryOptions(limit = FEED_PAGE).toWire())
        return unsigned.copy(auth = authFor(unsigned.encode(), owner))
    }

    private companion object {
        const val FLIPCASH_HOST = "fc-v2.api.flipcash-infra.net"
        const val GET_DM_CHAT_FEED = "/flipcash.chat.v1.Chat/GetDmChatFeed"
        const val GET_GROUP_CHAT_FEED = "/flipcash.chat.v1.Chat/GetGroupChatFeed"
        const val FEED_PAGE = 20
        val STREAM_WINDOW = 45.seconds
    }
}

/** Passes calls through and keeps every response, in order, with its method. */
private class RecordingTransport(private val inner: GrpcTransport) : GrpcTransport {
    val responses = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun unary(method: String, request: ByteArray): ByteArray =
        inner.unary(method, request).also { responses += method to it }

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> =
        inner.bidiStream(method, requests).onEach { responses += method to it }

    override fun close() = inner.close()
}

/**
 * Writes `<dir>/<platform>/<index>-<Method>.bin` (as received), `.reencoded.bin` (Wire's decode,
 * encoded again) and `.wire.json` (this module's decode, in the generator's describe shape).
 */
private class Dump(dir: String?, platform: String) {
    private val root = dir?.takeIf { it.isNotBlank() }?.let { "$it/$platform" }
    val enabled get() = root != null

    fun write(index: Int, method: String, bytes: ByteArray) {
        val root = root ?: return
        val base = "$root/${index.toString().padStart(3, '0')}-${method.substringAfterLast('/')}"
        writeLiveFile("$base.bin", bytes)
        val (reencoded, described) = when (method.substringAfterLast('/')) {
            "GetDmChatFeed" -> GetDmChatFeedResponse.ADAPTER.decode(bytes).let { it.encode() to describeFeed(it.chats) }
            "GetGroupChatFeed" -> GetGroupChatFeedResponse.ADAPTER.decode(bytes).let { it.encode() to describeFeed(it.chats) }
            "StreamEvents" -> StreamEventsResponse.ADAPTER.decode(bytes).encode() to describe(decodeStreamEvent(bytes))
            "GetReactors" -> GetReactorsResponse.ADAPTER.decode(bytes).encode() to describeReactors(bytes)
            else -> error("unexpected method $method")
        }
        writeLiveFile("$base.reencoded.bin", reencoded)
        writeLiveFile("$base.wire.json", described.toString().encodeToByteArray())
    }

    private fun describeFeed(chats: List<Metadata>): JsonObject = buildJsonObject {
        put("lastMessages", buildJsonArray { chats.mapNotNull { it.last_message }.forEach { add(describe(it.toDomain())) } })
    }

    private fun describeReactors(bytes: ByteArray): JsonObject = runCatching { decodeGetReactors(bytes) }.fold(
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
}

/** The variable [name] from the test process's environment. */
internal expect fun liveEnv(name: String): String?

/** A transport to [host]:443 over TLS, or null on a target without one. */
internal expect fun liveTransport(host: String): GrpcTransport?

internal expect fun writeLiveFile(path: String, bytes: ByteArray)

internal expect val livePlatform: String
