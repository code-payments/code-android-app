package com.flipcash.services.chat

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.events.v1.EventStreamingService as RpcEventStreamingService
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.flipcash.services.internal.network.extensions.toChatMessage
import com.google.protobuf.MessageLite
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Compares what :services:flipcash-client's LiveChatReadTest received from the production server
 * with what protobuf-java and Android's mappers make of the same bytes. For each response in
 * `FLIPCASH_LIVE_DUMP/<platform>/`:
 *  1. protobuf-java parses the received bytes and Wire's re-encoding to equal messages (so Wire
 *     dropped no field, known or unknown);
 *  2. Android's mapper and the Wire decode describe the same domain values.
 * Skipped when FLIPCASH_LIVE_DUMP is unset. Failures name the response and JSON path, not values.
 */
class LiveResponseParityTest {

    private val dump = System.getProperty("flipcashLiveDump")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun wireDecodesLiveResponsesLikeProtobufJava() {
        val dump = dump ?: return
        val platforms = dump.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        assertTrue(platforms.isNotEmpty(), "no platform directories in FLIPCASH_LIVE_DUMP")
        val problems = mutableListOf<String>()
        for (platform in platforms) {
            val received = platform.listFiles { f -> f.name.endsWith(".bin") && !f.name.endsWith(".reencoded.bin") }
                .orEmpty().sortedBy { it.name }
            var reorderedBytes = 0
            for (bin in received) {
                val base = bin.path.removeSuffix(".bin")
                val name = "${platform.name}/${bin.name.removeSuffix(".bin")}"
                val method = bin.name.substringAfter('-').removeSuffix(".bin")
                val bytes = bin.readBytes()
                val reencoded = File("$base.reencoded.bin").readBytes()
                val (original, roundTripped, android) = when (method) {
                    "GetDmChatFeed" -> parse(bytes, reencoded, RpcChatService.GetDmChatFeedResponse::parseFrom) { feed(it.chatsList) }
                    "GetGroupChatFeed" -> parse(bytes, reencoded, RpcChatService.GetGroupChatFeedResponse::parseFrom) { feed(it.chatsList) }
                    "StreamEvents" -> parse(bytes, reencoded, RpcEventStreamingService.StreamEventsResponse::parseFrom, ::describeStream)
                    "GetReactors" -> parse(bytes, reencoded, RpcMessagingService.GetReactorsResponse::parseFrom, ::describeGetReactors)
                    else -> error("unexpected method $method")
                }
                if (original != roundTripped) problems += "$name: Wire's re-encoding parses to a different protobuf-java message"
                if (!bytes.contentEquals(reencoded)) reorderedBytes++
                val wire = Json.parseToJsonElement(File("$base.wire.json").readText())
                diff(android, wire, "$name:").forEach { problems += it }
            }
            println("[${platform.name}] ${received.size} live responses compared; $reorderedBytes re-encode to different bytes")
        }
        val (unported, mismatches) = problems.partition { it.startsWith(UNPORTED) }
        println("${unported.size} content values are unported in the KMP decode (not counted as mismatches)")
        unported.forEach { println("  ${it.removePrefix(UNPORTED)}") }
        mismatches.forEach(::println)
        assertTrue(mismatches.isEmpty(), "${mismatches.size} live parity mismatches (listed above)")
    }

    private fun <T : MessageLite> parse(
        bytes: ByteArray,
        reencoded: ByteArray,
        parser: (ByteArray) -> T,
        describe: (T) -> JsonObject,
    ): Triple<T, T, JsonObject> {
        val original = parser(bytes)
        return Triple(original, parser(reencoded), describe(original))
    }

    private fun feed(chats: List<com.codeinc.flipcash.gen.chat.v1.Model.Metadata>): JsonObject = buildJsonObject {
        put("lastMessages", buildJsonArray { chats.filter { it.hasLastMessage() }.forEach { add(it.lastMessage.toChatMessage().describe()) } })
    }

    /** JSON paths where [a] and [b] differ; values are left out. */
    private fun diff(a: JsonElement, b: JsonElement, path: String): List<String> = when {
        a is JsonObject && b is JsonObject -> (a.keys + b.keys).flatMap { key ->
            val x = a[key]; val y = b[key]
            when {
                x == null -> listOf("$path.$key only in the Wire decode")
                y == null -> listOf("$path.$key only in the Android decode")
                else -> diff(x, y, "$path.$key")
            }
        }
        a is JsonArray && b is JsonArray ->
            if (a.size != b.size) listOf("$path has ${a.size} items on Android, ${b.size} on Wire")
            else a.indices.flatMap { diff(a[it], b[it], "$path[$it]") }
        a == b -> emptyList()
        // The Wire side writes this for content the KMP port does not cover (media, for one).
        b is JsonPrimitive && b.content == "unsupported" -> listOf("$UNPORTED$path")
        else -> listOf("$path differs")
    }
}


private const val UNPORTED = "unported: "
