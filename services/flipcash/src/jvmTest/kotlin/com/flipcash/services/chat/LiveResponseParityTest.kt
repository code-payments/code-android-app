package com.flipcash.services.chat

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.events.v1.EventStreamingService as RpcEventStreamingService
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
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
            val methods = mutableMapOf<String, Int>()
            for (bin in received) {
                val base = bin.path.removeSuffix(".bin")
                val name = "${platform.name}/${bin.name.removeSuffix(".bin")}"
                val method = bin.name.substringAfter('-').removeSuffix(".bin")
                val bytes = bin.readBytes()
                methods[method] = (methods[method] ?: 0) + 1
                val reencoded = File("$base.reencoded.bin").readBytes()
                val (original, roundTripped, android) = when (method) {
                    "GetDmChatFeed" -> parse(bytes, reencoded, RpcChatService.GetDmChatFeedResponse::parseFrom) { feed(it.chatsList) }
                    "GetGroupChatFeed" -> parse(bytes, reencoded, RpcChatService.GetGroupChatFeedResponse::parseFrom) { feed(it.chatsList) }
                    "StreamEvents" -> parse(bytes, reencoded, RpcEventStreamingService.StreamEventsResponse::parseFrom, ::describeStream)
                    "GetReactors" -> parse(bytes, reencoded, RpcMessagingService.GetReactorsResponse::parseFrom, ::describeGetReactors)
                    "GetReactionSummaries" -> parse(bytes, reencoded, RpcMessagingService.GetReactionSummariesResponse::parseFrom, ::describeGetReactionSummaries)
                    "GetMessages" -> parse(bytes, reencoded, RpcMessagingService.GetMessagesResponse::parseFrom, ::describeGetMessages)
                    "GetIntentMetadata" -> parse(bytes, reencoded, OcpTransactionService.GetIntentMetadataResponse::parseFrom, ::describeGetIntentMetadata)
                    else -> error("unexpected method $method")
                }
                if (original != roundTripped) problems += "$name: Wire's re-encoding parses to a different protobuf-java message"
                if (!bytes.contentEquals(reencoded)) reorderedBytes++
                val wire = Json.parseToJsonElement(File("$base.wire.json").readText())
                diff(android, wire, "$name:").forEach { problems += it }
            }
            println("[${platform.name}] ${received.size} live responses compared $methods; $reorderedBytes re-encode to different bytes")
        }
        val (unported, rest) = problems.partition { it.startsWith(UNPORTED) }
        val (known, others) = rest.partition { it.startsWith(DELETED_NANOS) }
        val mismatches = others
        println("${unported.size} content values are unported in the KMP decode (not counted as mismatches)")
        println("  by content case: " + unported.groupingBy { it.substringAfter(" (").substringBefore(")") }.eachCount())
        unported.forEach { println("  ${it.removePrefix(UNPORTED)}") }
        println(
            "${known.size} deleted-message timestamps keep their nanos in the KMP decode, which Android drops " +
                "(equal once the nanos are cleared; not counted as mismatches)",
        )
        known.forEach { println("  ${it.removePrefix(DELETED_NANOS)}") }
        val images = mismatches.filter { it.startsWith(PREVIEW_IMAGE) }
        println(
            "${images.size} link previews carry an image the KMP decode drops (media is unported; " +
                "equal once the image is cleared; not counted as mismatches)",
        )
        val real = mismatches.filterNot { it.startsWith(PREVIEW_IMAGE) }
        real.forEach(::println)
        assertTrue(real.isEmpty(), "${real.size} live parity mismatches (listed above)")
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
        b is JsonPrimitive && b.content == "unsupported" -> listOf("$UNPORTED$path" + contentCase(a) + (layout(((a as? JsonPrimitive)?.content?.hexOrNull()) ?: ByteArray(0))?.let { " {$it}" } ?: ""))
        sameDeletedToTheSecond(a, b) -> listOf("$DELETED_NANOS$path")
        sameWithoutPreviewImages(a, b) -> listOf("$PREVIEW_IMAGE$path")
        else -> listOf("$path differs" + tagHint(a, b))
    }

    /**
     * Android's `asTimestamp()` keeps only the seconds of a deletion time, so its re-encoded Deleted
     * content loses the nanos the KMP decode keeps. True when [a] (Android) and [b] (Wire) are both
     * Deleted content and equal once [b]'s nanos are cleared.
     */
    private fun sameDeletedToTheSecond(a: JsonElement, b: JsonElement): Boolean {
        val x = (a as? JsonPrimitive)?.content?.hexOrNull() ?: return false
        val y = (b as? JsonPrimitive)?.content?.hexOrNull() ?: return false
        val android = runCatching { MessagingModel.Content.parseFrom(x) }.getOrNull() ?: return false
        val wire = runCatching { MessagingModel.Content.parseFrom(y) }.getOrNull() ?: return false
        if (!android.hasDeleted() || !wire.hasDeleted() || wire.deleted.deletedTs.nanos == 0) return false
        val deleted = wire.deleted.toBuilder().setDeletedTs(wire.deleted.deletedTs.toBuilder().clearNanos()).build()
        val truncated = wire.toBuilder().setDeleted(deleted).build()
        return truncated == android
    }

    /** True when [a] (Android) equals [b] (Wire) once the images in [a]'s link previews are cleared. */
    private fun sameWithoutPreviewImages(a: JsonElement, b: JsonElement): Boolean {
        val x = (a as? JsonPrimitive)?.content?.hexOrNull() ?: return false
        val y = (b as? JsonPrimitive)?.content?.hexOrNull() ?: return false
        val android = runCatching { MessagingModel.Content.parseFrom(x) }.getOrNull() ?: return false
        val wire = runCatching { MessagingModel.Content.parseFrom(y) }.getOrNull() ?: return false
        val stripped = android.withoutPreviewImages()
        return stripped != android && stripped == wire
    }

    private fun MessagingModel.Content.withoutPreviewImages(): MessagingModel.Content = when {
        hasText() -> toBuilder().setText(
            text.toBuilder().clearLinkPreviews().addAllLinkPreviews(text.linkPreviewsList.map { it.toBuilder().clearImage().build() }),
        ).build()
        hasReply() -> toBuilder().setReply(
            reply.toBuilder().clearContent().addAllContent(reply.contentList.map { it.withoutPreviewImages() }),
        ).build()
        else -> this
    }

    /** Which `Content` case Android decoded [a] as, from its re-encoded bytes. */
    private fun contentCase(a: JsonElement): String {
        val bytes = (a as? JsonPrimitive)?.content?.hexOrNull() ?: return " (${(a as? JsonPrimitive)?.content?.substringBefore(':') ?: "?"})"
        val case = runCatching { MessagingModel.Content.parseFrom(bytes).typeCase.name }.getOrDefault("?")
        return " ($case)"
    }

    /** For two hex values, their protobuf field layouts (numbers and lengths, never the bytes). */
    private fun tagHint(a: JsonElement, b: JsonElement): String {
        val x = (a as? JsonPrimitive)?.content?.hexOrNull() ?: return ""
        val y = (b as? JsonPrimitive)?.content?.hexOrNull() ?: return ""
        return " (Android ${layout(x)}, Wire ${layout(y)})"
    }

    private fun String.hexOrNull(): ByteArray? =
        if (length % 2 == 0 && isNotEmpty() && all { it in "0123456789abcdef" }) chunked(2).map { it.toInt(16).toByte() }.toByteArray() else null

    private fun layout(bytes: ByteArray, depth: Int = 0): String? = runCatching {
        val input = com.google.protobuf.CodedInputStream.newInstance(bytes)
        buildList {
            while (!input.isAtEnd) {
                val tag = input.readTag(); val field = tag ushr 3
                when (tag and 7) {
                    0 -> { input.readRawVarint64(); add("${field}v") }
                    1 -> { input.readRawLittleEndian64(); add("${field}f64") }
                    5 -> { input.readRawLittleEndian32(); add("${field}f32") }
                    2 -> {
                        val sub = input.readByteArray()
                        val inner = if (depth < 4 && sub.isNotEmpty()) layout(sub, depth + 1) else null
                        add(if (inner != null) "$field[$inner]" else "${field}L${sub.size}")
                    }
                    else -> error("group")
                }
            }
        }.joinToString(" ")
    }.getOrNull()
}


private const val UNPORTED = "unported: "
private const val DELETED_NANOS = "deleted-nanos: "
private const val PREVIEW_IMAGE = "preview-image: "
