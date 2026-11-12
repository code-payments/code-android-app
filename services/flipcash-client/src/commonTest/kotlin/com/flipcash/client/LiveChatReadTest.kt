package com.flipcash.client

import com.codeinc.flipcash.wire.chat.v1.ChatType
import com.codeinc.flipcash.wire.chat.v1.GetDmChatFeedRequest
import com.codeinc.flipcash.wire.chat.v1.GetDmChatFeedResponse
import com.codeinc.flipcash.wire.chat.v1.GetGroupChatFeedRequest
import com.codeinc.flipcash.wire.chat.v1.GetGroupChatFeedResponse
import com.codeinc.flipcash.wire.chat.v1.Metadata
import com.codeinc.flipcash.wire.common.v1.ChatId as WireChatId
import com.codeinc.flipcash.wire.events.v1.StreamEventsResponse
import com.codeinc.flipcash.wire.messaging.v1.GetMessagesRequest
import com.codeinc.flipcash.wire.messaging.v1.GetMessagesResponse
import com.codeinc.flipcash.wire.messaging.v1.GetReactionSummariesRequest
import com.codeinc.flipcash.wire.messaging.v1.GetReactionSummariesResponse
import com.codeinc.flipcash.wire.messaging.v1.MessageIdBatch
import com.codeinc.flipcash.wire.messaging.v1.GetReactorsResponse
import com.codeinc.flipcash.wire.messaging.v1.Message
import com.codeinc.flipcash.wire.messaging.v1.ViewMode
import com.codeinc.opencode.wire.common.v1.IntentId
import com.codeinc.opencode.wire.common.v1.Signature
import com.codeinc.opencode.wire.common.v1.SolanaAccountId
import com.codeinc.opencode.wire.transaction.v1.GetIntentMetadataRequest
import com.codeinc.opencode.wire.transaction.v1.GetIntentMetadataResponse
import com.flipcash.chat.ContentCodec
import com.flipcash.chat.MessageContent
import com.getcode.crypt.DerivePath
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.transport.GrpcStatusException
import com.getcode.opencode.transport.GrpcTransport
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString

/**
 * Read-only calls from this module to the production Flipcash and OCP servers, as a test account.
 * Nothing here writes: no post, send, reaction, boost, join, key-envelope write or
 * registration.
 *
 *  1. The chat feeds (DM, and up to [MAX_GROUP_FEED_PAGES] group pages), for visible messages.
 *  2. GetMessages on the group chats, until one holds a message with a reaction. The first call signs
 *     Wire's own byte order on purpose: GetMessagesRequest has a oneof, which Wire writes after
 *     regular fields while the Go server re-marshals in field-number order before verifying.
 *  3. GetReactors on a reacted message (or a plain one if none is found).
 *  4. OCP GetIntentMetadata on the cash messages found, decoding any chat payment metadata.
 *  5. The event stream for [STREAM_WINDOW]: open, answer every ping, take what arrives, close.
 *
 * Skipped unless `FLIPCASH_LIVE=1` and the account is given, as `FLIPCASH_TEST_ACCESS_KEY_FILE` (the
 * path of a file holding its access key words, read in place). Prints counts and kinds only: never the key,
 * ids or content.
 *
 * With `FLIPCASH_LIVE_DUMP` set to a directory, each response is written there as received, beside
 * its Wire re-encoding and this module's decode, for `LiveResponseParityTest` in :services:flipcash
 * to compare against protobuf-java. Point it outside the repo: the bytes are the account's data.
 */
class LiveChatReadTest {

    @Test
    fun readOnlyChatCalls() = runTest(timeout = 240.seconds) {
        if (liveEnv("FLIPCASH_LIVE") != "1") return@runTest
        val account = liveAccount() ?: return@runTest
        val responses = mutableListOf<Pair<String, ByteArray>>()
        val flipcash = liveTransport(FLIPCASH_HOST)?.let { RecordingTransport(it, responses) } ?: run {
            println("[$livePlatform] no transport on this target; skipped")
            return@runTest
        }
        val ocp = RecordingTransport(requireNotNull(liveTransport(OCP_HOST)), responses)
        val owner = account.getSolanaKeyPair(DerivePath.primary)
        val dump = Dump(liveEnv("FLIPCASH_LIVE_DUMP"), livePlatform)

        // Real network waits: leave runTest's virtual clock, or a timeout would fire at once.
        withContext(Dispatchers.Default) {
            try {
                // 1. Chat feeds: the chats the account can see, each with its last message.
                val dmFeed = GetDmChatFeedResponse.ADAPTER.decode(
                    flipcash.unary(GET_DM_CHAT_FEED, dmFeedRequest(owner).encode()),
                )
                assertEquals(GetDmChatFeedResponse.Result.OK, dmFeed.result, "GetDmChatFeed")
                val groupChats = mutableListOf<Metadata>()
                var token: Id? = null
                var groupPages = 0
                do {
                    val page = GetGroupChatFeedResponse.ADAPTER.decode(
                        flipcash.unary(GET_GROUP_CHAT_FEED, groupFeedRequest(owner, token).encode()),
                    )
                    assertEquals(GetGroupChatFeedResponse.Result.OK, page.result, "GetGroupChatFeed")
                    groupChats += page.chats
                    groupPages++
                    token = page.paging_token?.value_?.toId()
                } while (page.has_more && token != null && groupPages < MAX_GROUP_FEED_PAGES)
                val chats = dmFeed.chats + groupChats
                println("[$livePlatform] feeds: ${dmFeed.chats.size} DM chats, ${groupChats.size} group chats ($groupPages pages)")
                assertTrue(chats.isNotEmpty(), "the test account sees no chats")
                val seen = chats.mapNotNull { chat -> chat.last_message?.let { chat to it } }.toMutableList()

                // 2. GetMessages: a page of each group chat, until one holds a message with a reaction.
                val withIds = groupChats.filter { it.chat_id != null }
                if (withIds.isNotEmpty()) {
                    val probeId = withIds.first().chat_id!!
                    val reordered = !messagesRequestBytes(probeId, fieldOrder = false)
                        .contentEquals(messagesRequestBytes(probeId, fieldOrder = true))
                    val fieldOrder = getMessages(flipcash, owner, probeId, signFieldOrder = true).first
                    val wireOrder = getMessages(flipcash, owner, probeId, signFieldOrder = false).first
                    // Negative control: a signature over other bytes. If this passes too, the server
                    // does not check this call's signature, and the two above prove nothing about order.
                    val tampered = getMessages(flipcash, owner, probeId, signFieldOrder = true, tamper = true).first
                    println(
                        "[$livePlatform] GetMessages on one chat, Wire's bytes ${if (reordered) "differ from" else "match"} " +
                            "field-number order; signed over field-number order: $fieldOrder, over Wire's order: $wireOrder, " +
                            "over other bytes: $tampered",
                    )
                    val outcomes = mutableMapOf<String, Int>()
                    var read = 0
                    for (chat in withIds) {
                        val (outcome, messages) = getMessages(flipcash, owner, chat.chat_id!!, signFieldOrder = true)
                        outcomes.increment(outcome)
                        messages.forEach { seen += chat to it }
                        read++
                        if (read >= MESSAGE_CHATS && messages.any { m -> m.reactions?.reactions?.any { it.count > 0 } == true }) break
                    }
                    println("[$livePlatform] GetMessages on $read group chats: $outcomes; ${seen.size} messages seen in all")
                    assertTrue(outcomes.keys.all { it == "OK" || it == "NOT_FOUND" }, "GetMessages outcomes $outcomes")
                    assertTrue((outcomes["OK"] ?: 0) > 0, "no GetMessages call returned OK")
                }
                reportContent(seen.map { it.second })

                // 3. GetReactors on a visible message: one with a reaction if any was found.
                // GetMessages leaves reactions out, so ask GetReactionSummaries (a read) per chat.
                val reacted = findReacted(flipcash, owner, seen)
                val (chat, message, emoji) = reacted ?: seen.first().let { Triple(it.first, it.second, "👍") }
                val page = MessagingClient(flipcash).getReactors(
                    owner = owner,
                    chatId = chat.chat_id?.value_.toId(),
                    messageId = message.message_id!!.value_,
                    emoji = emoji,
                )
                println(
                    "[$livePlatform] GetReactors on a ${if (reacted != null) "reacted" else "plain (none reacted)"} message: " +
                        "OK, ${page.reactors.size} reactors, hasMore=${page.hasMore}",
                )
                if (reacted != null) assertTrue(page.reactors.isNotEmpty(), "a reacted message returned no reactors")

                // 4. Payment metadata: the cash messages' intents, through OCP GetIntentMetadata.
                val intents = seen.flatMap { it.second.content }
                    .mapNotNull { (ContentCodec.decode(it.encode()) as? MessageContent.Cash)?.intentId }
                    .distinctBy { it.toList() }
                    .take(MAX_INTENTS)
                val results = mutableMapOf<String, Int>()
                val kinds = mutableMapOf<String, Int>()
                for (intent in intents) {
                    val response = GetIntentMetadataResponse.ADAPTER.decode(
                        ocp.unary(GET_INTENT_METADATA, intentMetadataRequest(owner, intent).encode()),
                    )
                    results.increment(response.result.name)
                    val appMetadata = response.metadata?.app_metadata?.value_ ?: continue
                    val kind = describe(PaymentMetadataCodec.decode(appMetadata.toByteArray()))["kind"]!!.jsonPrimitive.content
                    kinds.increment(kind)
                }
                println("[$livePlatform] GetIntentMetadata on ${intents.size} cash intents: results $results; chat payment metadata kinds $kinds")

                // 5. The event stream: open, answer every ping, take what arrives, close at the window's end.
                var pings = 0
                var batches = 0
                var updates = 0
                withTimeoutOrNull(STREAM_WINDOW) {
                    EventStreamClient(flipcash).events(owner).collect { event ->
                        when (event) {
                            is StreamEvent.Ping -> pings++
                            is StreamEvent.ChatUpdates -> { batches++; updates += event.updates.size }
                            is StreamEvent.Error -> fail("stream error ${event.code}")
                        }
                    }
                    fail("the server ended the stream before the ${STREAM_WINDOW.inWholeSeconds}s window")
                }
                val sent = flipcash.sentFrames
                println(
                    "[$livePlatform] stream over ${STREAM_WINDOW.inWholeSeconds}s: $pings pings, $sent frames sent " +
                        "(open + pongs), $batches event batches ($updates chat updates); closed by us",
                )
                assertTrue(pings >= 2, "fewer than two pings in ${STREAM_WINDOW.inWholeSeconds}s")
                // The open, then one pong per ping; the last pong may lose the race with our close.
                assertTrue(sent in pings..pings + 1, "sent $sent frames for $pings pings")
            } finally {
                flipcash.close()
                ocp.close()
            }
        }

        responses.forEachIndexed { index, (method, bytes) -> dump.write(index, method, bytes) }
        println("[$livePlatform] ${responses.size} responses${if (dump.enabled) " written to FLIPCASH_LIVE_DUMP" else ""}")
    }

    /**
     * One newest-first page of [chatId]. With [signFieldOrder] the signature covers the request's
     * fields in field-number order, as protobuf-java and Go write them; without it, Wire's encoding.
     */
    private suspend fun getMessages(
        transport: GrpcTransport,
        owner: Ed25519.KeyPair,
        chatId: WireChatId,
        signFieldOrder: Boolean,
        tamper: Boolean = false,
    ): Pair<String, List<Message>> {
        val unsigned = messagesRequest(chatId)
        val signed = messagesRequestBytes(chatId, signFieldOrder).let { if (tamper) it + byteArrayOf(0x50, 0x01) else it }
        val request = unsigned.copy(auth = authFor(signed, owner))
        return try {
            val response = GetMessagesResponse.ADAPTER.decode(transport.unary(GET_MESSAGES, request.encode()))
            response.result.name to response.messages?.messages.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GrpcStatusException) {
            "rpc failed (gRPC status ${e.code})" to emptyList()
        } catch (e: Exception) {
            "rpc failed (${e::class.simpleName})" to emptyList()
        }
    }

    /**
     * The first message with a reaction, from GetReactionSummaries over the newest [MESSAGE_PAGE]
     * messages of up to [SUMMARY_CHATS] chats, busiest first.
     */
    private suspend fun findReacted(
        transport: GrpcTransport,
        owner: Ed25519.KeyPair,
        seen: List<Pair<Metadata, Message>>,
    ): Triple<Metadata, Message, String>? {
        val byChat = seen.groupBy { it.first.chat_id!!.value_ }.values.sortedByDescending { it.size }.take(SUMMARY_CHATS)
        val outcomes = mutableMapOf<String, Int>()
        var found: Triple<Metadata, Message, String>? = null
        for (pairs in byChat) {
            val chat = pairs.first().first
            val messages = pairs.map { it.second }.take(100)
            val unsigned = GetReactionSummariesRequest(
                chat_id = chat.chat_id,
                message_ids = MessageIdBatch(message_ids = messages.map { it.message_id!! }),
            )
            // No auth at signing time, so Wire's bytes are already in field-number order (1, 3).
            val request = unsigned.copy(auth = authFor(unsigned.encode(), owner))
            val response = try {
                GetReactionSummariesResponse.ADAPTER.decode(transport.unary(GET_REACTION_SUMMARIES, request.encode()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcomes.increment("rpc failed (${e::class.simpleName})"); continue
            }
            outcomes.increment(response.result.name)
            val hit = response.summaries.firstNotNullOfOrNull { summary ->
                summary.reactions.firstOrNull { it.count > 0 }?.let { reaction ->
                    messages.firstOrNull { it.message_id == summary.message_id }?.let { Triple(chat, it, reaction.emoji?.value_.orEmpty()) }
                }
            }
            if (hit != null) { found = hit; break }
        }
        println("[$livePlatform] GetReactionSummaries on ${outcomes.values.sum()} chats: $outcomes; reacted message ${if (found != null) "found" else "not found"}")
        return found
    }

    private fun messagesRequest(chatId: WireChatId) = GetMessagesRequest(
        chat_id = chatId,
        options = QueryOptions(limit = MESSAGE_PAGE).toWire(),
        view_mode = ViewMode.FULL_OR_REDACTED,
    )

    /** The unsigned request's bytes: Wire's encoding, or its fields concatenated in field-number order. */
    private fun messagesRequestBytes(chatId: WireChatId, fieldOrder: Boolean): ByteArray {
        val request = messagesRequest(chatId)
        if (!fieldOrder) return request.encode()
        return GetMessagesRequest(chat_id = request.chat_id).encode() +
            GetMessagesRequest(options = request.options).encode() +
            GetMessagesRequest(view_mode = request.view_mode).encode()
    }

    /** Counts the kinds of the messages seen; the content itself is not printed. */
    private fun reportContent(messages: List<Message>) {
        val kinds = messages.flatMap { it.content }
            .map { ContentCodec.decode(it.encode())::class.simpleName ?: "?" }
            .groupingBy { it }.eachCount()
        val reacted = messages.count { m -> m.reactions?.reactions?.any { it.count > 0 } == true }
        println("[$livePlatform] content kinds of ${messages.size} messages: $kinds; $reacted with reactions")
    }

    private fun dmFeedRequest(owner: Ed25519.KeyPair): GetDmChatFeedRequest {
        val unsigned = GetDmChatFeedRequest(query_options = QueryOptions(limit = FEED_PAGE).toWire(), dm_chat_type = ChatType.DM)
        return unsigned.copy(auth = authFor(unsigned.encode(), owner))
    }

    private fun groupFeedRequest(owner: Ed25519.KeyPair, token: Id?): GetGroupChatFeedRequest {
        val unsigned = GetGroupChatFeedRequest(query_options = QueryOptions(limit = FEED_PAGE, token = token).toWire())
        return unsigned.copy(auth = authFor(unsigned.encode(), owner))
    }

    /** Signed as Android's TransactionApi signs it: the request without its signature. */
    private fun intentMetadataRequest(owner: Ed25519.KeyPair, intent: ByteArray): GetIntentMetadataRequest {
        val unsigned = GetIntentMetadataRequest(
            intent_id = IntentId(value_ = intent.toByteString()),
            owner = SolanaAccountId(value_ = owner.publicKeyBytes.toByteString()),
        )
        return unsigned.copy(signature = Signature(value_ = Ed25519.sign(unsigned.encode(), owner).toByteString()))
    }

    /** The test account, from the key file. */
    private fun liveAccount(): MnemonicPhrase? {
        liveEnv("FLIPCASH_TEST_ACCESS_KEY_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
            val words = liveReadFile(path).trim().split(Regex("\\s+"))
            return requireNotNull(MnemonicPhrase.newInstance(words)) { "FLIPCASH_TEST_ACCESS_KEY_FILE does not hold 12 or 24 words" }
        }
        return null
    }

    private fun MutableMap<String, Int>.increment(key: String) { this[key] = (this[key] ?: 0) + 1 }

    private companion object {
        const val FLIPCASH_HOST = "fc-v2.api.flipcash-infra.net"
        const val OCP_HOST = "ocp-v2.api.flipcash-infra.net"
        const val GET_DM_CHAT_FEED = "/flipcash.chat.v1.Chat/GetDmChatFeed"
        const val GET_GROUP_CHAT_FEED = "/flipcash.chat.v1.Chat/GetGroupChatFeed"
        const val GET_MESSAGES = "/flipcash.messaging.v1.Messaging/GetMessages"
        const val GET_REACTION_SUMMARIES = "/flipcash.messaging.v1.Messaging/GetReactionSummaries"
        const val SUMMARY_CHATS = 12
        const val GET_INTENT_METADATA = "/ocp.transaction.v1.Transaction/GetIntentMetadata"
        const val FEED_PAGE = 20
        const val MAX_GROUP_FEED_PAGES = 3
        const val MESSAGE_CHATS = 4
        const val MESSAGE_PAGE = 50
        const val MAX_INTENTS = 6
        val STREAM_WINDOW = 45.seconds
    }
}

/** Passes calls through, keeps every response in order with its method, and counts stream frames sent. */
private class RecordingTransport(
    private val inner: GrpcTransport,
    val responses: MutableList<Pair<String, ByteArray>>,
) : GrpcTransport {
    var sentFrames = 0
        private set

    override suspend fun unary(method: String, request: ByteArray): ByteArray =
        inner.unary(method, request).also { responses += method to it }

    override fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray> =
        inner.bidiStream(method, requests.onEach { sentFrames++ }).onEach { responses += method to it }

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
            "GetMessages" -> GetMessagesResponse.ADAPTER.decode(bytes).let { it.encode() to describeMessages(it) }
            "GetIntentMetadata" -> GetIntentMetadataResponse.ADAPTER.decode(bytes).let { it.encode() to describeIntentMetadata(it) }
            "StreamEvents" -> StreamEventsResponse.ADAPTER.decode(bytes).encode() to describe(decodeStreamEvent(bytes))
            "GetReactors" -> GetReactorsResponse.ADAPTER.decode(bytes).encode() to describeReactors(bytes)
            "GetReactionSummaries" -> GetReactionSummariesResponse.ADAPTER.decode(bytes).let { it.encode() to describeSummaries(it) }
            else -> error("unexpected method $method")
        }
        writeLiveFile("$base.reencoded.bin", reencoded)
        writeLiveFile("$base.wire.json", described.toString().encodeToByteArray())
    }

    private fun describeFeed(chats: List<Metadata>): JsonObject = buildJsonObject {
        put("lastMessages", buildJsonArray { chats.mapNotNull { it.last_message }.forEach { add(describe(it.toDomain())) } })
    }

    private fun describeMessages(response: GetMessagesResponse): JsonObject = buildJsonObject {
        put("result", response.result.name)
        put("messages", buildJsonArray { response.messages?.messages.orEmpty().forEach { add(describe(it.toDomain())) } })
    }

    /** Raw fields: the client has no domain type for reaction summaries. */
    private fun describeSummaries(response: GetReactionSummariesResponse): JsonObject = buildJsonObject {
        put("result", response.result.name)
        put("summaries", buildJsonArray {
            response.summaries.forEach { summary ->
                add(buildJsonObject {
                    put("messageId", summary.message_id?.value_ ?: 0L)
                    put("reactions", buildJsonArray {
                        summary.reactions.forEach { add(buildJsonObject { put("emoji", it.emoji?.value_.orEmpty()); put("count", it.count) }) }
                    })
                })
            }
        })
    }

    private fun describeIntentMetadata(response: GetIntentMetadataResponse): JsonObject = buildJsonObject {
        put("result", response.result.name)
        response.metadata?.app_metadata?.value_?.let { value ->
            val metadata = PaymentMetadataCodec.decode(value.toByteArray())
            put("chatPayment", describe(metadata))
            metadata?.let { put("chatId", it.chatId.bytes.hex()) }
        }
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

internal expect fun liveReadFile(path: String): String

internal expect fun writeLiveFile(path: String, bytes: ByteArray)

internal expect val livePlatform: String
