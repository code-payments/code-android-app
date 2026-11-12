package com.flipcash.client

import com.getcode.opencode.transport.canonicalize

import com.flipcash.chat.ContentCodec
import com.flipcash.chat.MessageContent
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Describes decoded values in the JSON shape `ChatClientFixtureTest` in :services:flipcash writes
// for Android's decode, so the two sides compare as JSON. Shared by the parity and live tests.

internal fun describe(metadata: ChatPaymentMetadata?): JsonObject = buildJsonObject {
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

internal fun describe(event: StreamEvent?): JsonObject = buildJsonObject {
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

internal fun describe(page: ReactorsPage): JsonObject = buildJsonObject {
    put("type", "page")
    put("reactors", buildJsonArray { page.reactors.forEach { add(describe(it)) } })
    put("hasMore", page.hasMore)
    page.pagingToken?.let { put("pagingToken", it.bytes.hex()) }
}

internal fun describe(update: ChatUpdate): JsonObject = buildJsonObject {
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

internal fun describe(message: ChatMessage): JsonObject = buildJsonObject {
    put("messageId", message.messageId)
    message.senderId?.let { put("senderId", it.bytes.hex()) }
    // Content this port does not cover has no wire form to compare; LiveResponseParityTest counts it.
    put("content", buildJsonArray {
        message.content.forEach { add(describeContent(it)) }
    })
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

internal fun describe(reactor: Reactor): JsonObject = buildJsonObject {
    put("userId", reactor.userId.bytes.hex()); put("reactedAt", reactor.reactedAt.describe())
    put("version", reactor.version); put("boostCents", reactor.boostCents)
}

internal fun Instant.describe(): String = "$epochSeconds.${nanosecondsOfSecond.toString().padStart(9, '0')}"

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/**
 * Content as its re-encoded bytes in canonical (protobuf-java) order. A share-profile widget, which only the server writes and so has
 * no client encoder, is described by its username; anything else the encoder rejects is
 * [UNSUPPORTED_CONTENT].
 */
internal fun describeContent(content: MessageContent): String = runCatching { canonicalContent(ContentCodec.encode(content)).hex() }
    .getOrElse { (content as? MessageContent.Widget.ShareProfile)?.let { "share-profile:${it.username}" } ?: UNSUPPORTED_CONTENT }

/** What [describe] puts for content the port decodes to an Unsupported value. */
internal const val UNSUPPORTED_CONTENT = "unsupported"

/** `Content` bytes in field-number order under the flipcash2 schema, as protobuf-java writes them. */
internal fun canonicalContent(bytes: ByteArray): ByteArray =
    canonicalize(bytes, "flipcash.messaging.v1.Content", FlipcashCanonicalSchema)
