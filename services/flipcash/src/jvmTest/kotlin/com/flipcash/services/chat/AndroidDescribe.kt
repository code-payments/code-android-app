package com.flipcash.services.chat

import com.codeinc.flipcash.gen.events.v1.EventStreamingService as RpcEventStreamingService
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.toChatUpdate
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.extensions.toReactor
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMutation
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.Reactor
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// What Android's protobuf-java mappers produce, as JSON values. Shared by ChatClientFixtureTest
// (which records them) and LiveResponseParityTest (which compares them with the Wire decode).

    /** What openStream's response handler does with [response], as values. */
internal fun describeStream(response: RpcEventStreamingService.StreamEventsResponse): JsonObject = buildJsonObject {
    when (response.typeCase) {
        RpcEventStreamingService.StreamEventsResponse.TypeCase.PING -> {
            put("type", "ping")
            put("serverTime", Instant.fromEpochSeconds(response.ping.timestamp.seconds, response.ping.timestamp.nanos).describe())
            put("pingDelaySeconds", response.ping.pingDelay.seconds)
        }
        RpcEventStreamingService.StreamEventsResponse.TypeCase.ERROR -> {
            put("type", "error")
            put("code", when (response.error.code) {
                RpcEventStreamingService.StreamEventsResponse.StreamError.Code.DENIED -> "DENIED"
                RpcEventStreamingService.StreamEventsResponse.StreamError.Code.INVALID_TIMESTAMP -> "INVALID_TIMESTAMP"
                else -> "UNRECOGNIZED"
            })
        }
        RpcEventStreamingService.StreamEventsResponse.TypeCase.EVENTS -> {
            put("type", "updates")
            put("updates", buildJsonArray {
                response.events.eventsList
                    .filter { it.typeCase == EventModel.Event.TypeCase.CHAT_UPDATE }
                    .forEach { add(it.chatUpdate.toChatUpdate().describe()) }
            })
        }
        else -> put("type", "none")
    }
}

    /** What `ChatMessagingService.getReactors` returns for [response], as values. */
internal fun describeGetReactors(response: RpcMessagingService.GetReactorsResponse): JsonObject = buildJsonObject {
    when (response.result) {
        RpcMessagingService.GetReactorsResponse.Result.OK -> {
            put("type", "page")
            put("reactors", buildJsonArray { response.reactorsList.forEach { add(it.toReactor().describe()) } })
            put("hasMore", response.hasMore)
            if (response.hasPagingToken()) put("pagingToken", response.pagingToken.toPagingToken().hex())
        }
        RpcMessagingService.GetReactorsResponse.Result.DENIED -> put("type", "denied")
        RpcMessagingService.GetReactorsResponse.Result.MESSAGE_NOT_FOUND -> put("type", "message-not-found")
        else -> put("type", "unrecognized")
    }
}

internal fun ChatUpdate.describe(): JsonObject = buildJsonObject {
    put("chatId", chatId.bytes.hex())
    put("pointers", buildJsonArray {
        pointerUpdates.forEach { p ->
            add(buildJsonObject {
                put("type", p.type.name); put("userId", p.userId.hex()); put("value", p.value); put("ts", p.timestamp.describe())
            })
        }
    })
    put("typing", buildJsonArray {
        typingNotifications.forEach { t ->
            add(buildJsonObject { put("userId", t.userId.hex()); put("state", t.state.name) })
        }
    })
    put("events", buildJsonArray {
        events.forEach { e ->
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
                            put("message", m.message.describe())
                        })
                    }
                })
            })
        }
    })
    put("reactionUpdates", buildJsonArray {
        reactionUpdates.forEach { u ->
            add(buildJsonObject {
                put("messageId", u.messageId); put("emoji", u.emoji.value); put("actor", u.actor.hex())
                put("action", u.action.name); put("count", u.count); put("version", u.version)
                put("reactedAt", u.reactedAt.describe()); put("boostCents", u.boostCents); put("actorBoostCents", u.actorBoostCents)
            })
        }
    })
}

internal fun ChatMessage.describe(): JsonObject = buildJsonObject {
    put("messageId", messageId)
    senderId?.let { put("senderId", it.hex()) }
    // Content decode is gated by :services:flipcash-chat's vectors; here it is compared by its
    // re-encoded bytes.
    put("content", buildJsonArray { content.forEach { add(it.asContent().toByteArray().hex()) } })
    put("ts", timestamp.describe())
    put("unreadSeq", unreadSeq)
    lastEditedTs?.let { put("lastEditedTs", it.describe()) }
    put("eventSequence", eventSequence)
    reactions?.let { s ->
        put("reactions", buildJsonObject {
            put("messageId", s.messageId)
            put("reactions", buildJsonArray { s.reactions.forEach { add(it.describe()) } })
        })
    }
    put("redacted", redacted)
}

internal fun EmojiReaction.describe(): JsonObject = buildJsonObject {
    put("emoji", emoji.value); put("count", count)
    selfReactor?.let { put("selfReactor", it.describe()) }
    put("sampleReactors", buildJsonArray { sampleReactors.forEach { add(it.describe()) } })
    put("version", version); put("boostCents", boostCents)
}

internal fun Reactor.describe(): JsonObject = buildJsonObject {
    put("userId", userId.hex()); put("reactedAt", reactedAt.describe()); put("version", version); put("boostCents", boostCents)
}

internal fun Instant.describe(): String = "$epochSeconds.${nanosecondsOfSecond.toString().padStart(9, '0')}"
