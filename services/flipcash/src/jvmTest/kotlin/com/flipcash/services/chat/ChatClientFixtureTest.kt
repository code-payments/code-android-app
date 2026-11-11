package com.flipcash.services.chat

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.events.v1.EventStreamingGrpc
import com.codeinc.flipcash.gen.events.v1.EventStreamingService as RpcEventStreamingService
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.codeinc.flipcash.gen.intent.v1.Model as FlipcashIntentModel
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.api.ChatMessagingApi
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.toChatUpdate
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.extensions.toReactor
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.buildDmPaymentMetadata
import com.flipcash.services.models.buildGroupPaymentMetadata
import com.flipcash.services.models.buildReactionBoostMetadata
import com.flipcash.services.models.buildRequestedPaymentMetadata
import com.flipcash.services.models.buildReturnPaymentMetadata
import com.flipcash.services.models.buildTipDmPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMutation
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.Reactor
import com.getcode.ed25519kmp.Ed25519
import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import java.io.File
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Records `flipcash_client.json` for `:services:flipcash-client`: the request bytes Android's
 * protobuf-java code sends for the event stream and GetReactors, the `AppMetadata` its payment
 * builders produce, and responses with the domain values Android decodes them to.
 *
 * GetReactors goes through the real [ChatMessagingApi] into a channel that records the request and
 * fails the call. The stream's open and pong are built inside `EventStreamingService.openStream`
 * from `Clock.System.now()`, so they are rebuilt here with the same builder calls and the real
 * `authenticate()`, at a fixed time. Responses are built with protobuf-java, since none can be
 * recorded from the server without an account.
 *
 * Fails if Android's output drifts from the file; run with `-Dfixture.update=true` to rewrite it.
 */
class ChatClientFixtureTest {

    private val vectors = File(requireNotNull(System.getProperty("chatCipherVectors")) { "chatCipherVectors not set" })
    private val fixture = File(requireNotNull(System.getProperty("flipcashClientFixture")) { "flipcashClientFixture not set" })

    private val chatCipher = readJson(File(vectors, "chat_cipher.json"))
    private val groupCipher = readJson(File(vectors, "group_cipher.json"))
    private val alice = chatCipher.list("keyConversion").first { it.str("name") == "alice" }
    private val owner = Ed25519.createKeyPair(alice.str("edSeed").unhex())
    private val chatId = groupCipher.list("wraps").first().str("chatId").unhex()

    private val userA = ByteArray(16) { (0x10 + it).toByte() }
    private val userB = ByteArray(16) { (0x30 + it).toByte() }
    private val mint = ByteArray(32) { (0xC0 + it).toByte() }
    private val intentId = ByteArray(32) { (0x50 + it).toByte() }
    private val paymentRequestId = ByteArray(32) { (0x70 + it).toByte() }
    private val pagingToken = ByteArray(8) { (0xA0 + it).toByte() }

    @Test
    fun androidOutputMatchesFixture() = runBlocking {
        val captured = buildJsonObject {
            put("ownerSeed", alice.str("edSeed"))
            put("chatId", chatId.hex())
            put("requests", requests())
            put("paymentMetadata", paymentMetadata())
            put("streamResponses", streamResponses())
            put("getReactorsResponses", getReactorsResponses())
        }
        checkOrUpdateFixture(fixture, captured)
    }

    // -- requests --

    private suspend fun requests(): JsonObject = buildJsonObject {
        put("streamEventsPath", "/" + EventStreamingGrpc.getStreamEventsMethod().fullMethodName)
        put("streamEventsOpen", buildJsonArray {
            listOf(1_762_000_000L to 0, 1_762_000_000L to 123_456_789).forEach { (seconds, nanos) ->
                // EventStreamingService.openStream's initial request, at a fixed ts.
                val params = RpcEventStreamingService.StreamEventsRequest.Params.newBuilder()
                    .setTs(timestamp(seconds, nanos))
                    .apply { setAuth(authenticate(owner)) }
                    .build()
                val request = RpcEventStreamingService.StreamEventsRequest.newBuilder().setParams(params).build()
                add(buildJsonObject { put("seconds", seconds); put("nanos", nanos); put("bytes", request.toByteArray().hex()) })
            }
        })
        put("pong", buildJsonArray {
            listOf(1_762_000_015L to 0, 1_762_000_015L to 999_999_999).forEach { (seconds, nanos) ->
                // The pong openStream sends for each ping.
                val request = RpcEventStreamingService.StreamEventsRequest.newBuilder()
                    .setPong(EventModel.ClientPong.newBuilder().setTimestamp(timestamp(seconds, nanos)))
                    .build()
                add(buildJsonObject { put("seconds", seconds); put("nanos", nanos); put("bytes", request.toByteArray().hex()) })
            }
        })

        val channel = CapturingChannel()
        val api = ChatMessagingApi(channel)
        put("getReactors", buildJsonArray {
            listOf(
                Triple("defaults", 42L, QueryOptions()),
                Triple("ascending-with-token", 7L, QueryOptions(limit = 25, token = pagingToken.toList(), descending = false)),
            ).forEach { (name, messageId, options) ->
                val emoji = "🔥"
                runCatching { api.getReactors(owner, ChatId(chatId), messageId, Emoji(emoji), options) }
                val bytes = channel.takeOnly()
                add(buildJsonObject {
                    put("name", name)
                    put("path", "/" + channel.methods.removeLast())
                    put("messageId", messageId)
                    put("emoji", emoji)
                    put("limit", options.limit)
                    put("descending", options.descending)
                    options.token?.let { put("token", it.hex()) }
                    put("bytes", bytes.hex())
                })
            }
        })
    }

    // -- payment metadata --

    private fun paymentMetadata(): JsonArray = buildJsonArray {
        val id = ChatId(chatId)
        fun entry(name: String, bytes: ByteArray?, fields: JsonObjectBuilder.() -> Unit) = add(buildJsonObject {
            put("name", name)
            fields()
            put("bytes", requireNotNull(bytes).hex())
        })
        entry("group", buildGroupPaymentMetadata(id)) { put("kind", "group") }
        entry("contact-dm", buildDmPaymentMetadata(id, "+15555550100", "+15555550199")) {
            put("kind", "contactDm"); put("sourcePhone", "+15555550100"); put("destinationPhone", "+15555550199")
        }
        entry("tip-dm-tipcard-tip", buildTipDmPaymentMetadata(id, TipOrigin.TIPCARD, TipAction.TIP)) {
            put("kind", "tipDm"); put("origin", "TIPCARD"); put("action", "TIP")
        }
        entry("tip-dm-chat-send", buildTipDmPaymentMetadata(id, TipOrigin.CHAT, TipAction.SEND)) {
            put("kind", "tipDm"); put("origin", "CHAT"); put("action", "SEND")
        }
        entry("return", buildReturnPaymentMetadata(id, 9_001L)) { put("kind", "return"); put("messageId", 9_001L) }
        entry("requested", buildRequestedPaymentMetadata(id, paymentRequestId)) {
            put("kind", "requested"); put("paymentRequestId", paymentRequestId.hex())
        }
        entry("reaction-boost", buildReactionBoostMetadata(id, 42L, Emoji("❤️"))) {
            put("kind", "reactionBoost"); put("messageId", 42L); put("emoji", "❤️")
        }
        // Decode-only: no Android builder writes these.
        val unsetAction = FlipcashIntentModel.AppMetadata.newBuilder().setChat(
            FlipcashIntentModel.ChatMetadata.newBuilder()
                .setChatId(Common.ChatId.newBuilder().setValue(chatId.bs()))
                .setDmPayment(
                    FlipcashIntentModel.ChatMetadata.DmPayment.newBuilder()
                        .setLocation(FlipcashIntentModel.ChatMetadata.DmPayment.Location.CHAT)
                )
        ).build().toByteArray()
        entry("tip-dm-unset-action", unsetAction) { put("kind", "tipDm"); put("origin", "CHAT"); put("decodeOnly", true) }
        entry("no-chat", FlipcashIntentModel.AppMetadata.getDefaultInstance().toByteArray()) {
            put("kind", "none"); put("decodeOnly", true)
        }
    }

    // -- stream responses --

    private fun streamResponses(): JsonArray = buildJsonArray {
        fun entry(name: String, response: RpcEventStreamingService.StreamEventsResponse) = add(buildJsonObject {
            put("name", name)
            put("bytes", response.toByteArray().hex())
            put("expected", describeStream(response))
        })
        val r = RpcEventStreamingService.StreamEventsResponse.newBuilder()
        entry("ping", r.clone().setPing(
            EventModel.ServerPing.newBuilder()
                .setTimestamp(timestamp(1_762_000_010L, 500_000_000))
                .setPingDelay(Duration.newBuilder().setSeconds(15))
        ).build())
        listOf("error-denied" to 0, "error-invalid-timestamp" to 1, "error-unknown-code" to 42).forEach { (name, code) ->
            entry(name, r.clone().setError(
                RpcEventStreamingService.StreamEventsResponse.StreamError.newBuilder().setCodeValue(code)
            ).build())
        }
        entry("empty", r.clone().build())
        entry("events", r.clone().setEvents(eventBatch()).build())
    }

    private fun eventBatch(): EventModel.EventBatch {
        val chat = Common.ChatId.newBuilder().setValue(chatId.bs())
        val reactor = MessagingModel.Reactor.newBuilder()
            .setUserId(userId(userB)).setReactedTs(timestamp(1_762_000_002L, 0)).setVersion(3).setBoostUsd(0.29)
        val message = MessagingModel.Message.newBuilder()
            .setMessageId(MessagingModel.MessageId.newBuilder().setValue(42))
            .setSenderId(userId(userA))
            .addContent(text("gm"))
            // A notice with no text: Android drops the content and keeps the message.
            .addContent(MessagingModel.Content.newBuilder().setSystem(MessagingModel.SystemContent.newBuilder().setFallbackText(" \n")))
            .setTs(timestamp(1_762_000_001L, 250_000_000))
            .setUnreadSeq(5)
            .setEventSequence(11)
            .setReactions(
                MessagingModel.ReactionSummary.newBuilder()
                    .setMessageId(MessagingModel.MessageId.newBuilder().setValue(42))
                    .addReactions(
                        MessagingModel.EmojiReaction.newBuilder()
                            .setEmoji(MessagingModel.Emoji.newBuilder().setValue("🔥"))
                            .setCount(2).setSelfReactor(reactor).addSampleReactors(reactor).setVersion(4).setBoostUsd(1.005)
                    )
            )
        val edited = message.clone().clearContent().addContent(text("gm, edited"))
            .setLastEditedTs(timestamp(1_762_000_003L, 0))
        val cash = MessagingModel.Content.newBuilder().setCash(
            MessagingModel.CashContent.newBuilder()
                .setIntentId(Common.IntentId.newBuilder().setValue(intentId.bs()))
                .setTotalAmount(
                    Common.CryptoPaymentAmount.newBuilder()
                        .setQuarks(1_500_000)
                        .setMint(Common.PublicKey.newBuilder().setValue(mint.bs()))
                )
                .setVerb(MessagingModel.CashContent.Verb.SENT)
        )
        val update = EventModel.ChatUpdate.newBuilder()
            .setChat(chat)
            .setEvents(
                MessagingModel.EventBatch.newBuilder()
                    .addEvents(
                        MessagingModel.Event.newBuilder()
                            .setSequence(11).setCount(3).setTs(timestamp(1_762_000_004L, 1))
                            .addMutations(MessagingModel.Mutation.newBuilder().setMessageSent(message))
                            .addMutations(MessagingModel.Mutation.newBuilder().setMessageEdited(edited))
                            .addMutations(MessagingModel.Mutation.newBuilder().setMessageDeleted(
                                // No sender: the server's own notice.
                                MessagingModel.Message.newBuilder().setMessageId(MessagingModel.MessageId.newBuilder().setValue(43))
                                    .addContent(cash).setTs(timestamp(1_762_000_005L, 0)).setRedacted(true)
                            ))
                            // A mutation kind this build does not know: Android maps it to a sent default message.
                            .addMutations(MessagingModel.Mutation.getDefaultInstance())
                    )
            )
            .setPointerUpdates(
                MessagingModel.PointerBatch.newBuilder()
                    .addPointers(pointer(MessagingModel.Pointer.Type.SENT.number, 42))
                    .addPointers(pointer(MessagingModel.Pointer.Type.READ.number, 41))
                    .addPointers(pointer(9, 40))
            )
            .setIsTypingNotifications(
                MessagingModel.IsTypingNotificationBatch.newBuilder()
                    .addIsTypingNotifications(typing(MessagingModel.IsTypingNotification.State.STARTED_TYPING.number))
                    .addIsTypingNotifications(typing(MessagingModel.IsTypingNotification.State.TYPING_TIMED_OUT.number))
                    .addIsTypingNotifications(typing(9))
            )
            .setReactionUpdates(
                MessagingModel.ReactionUpdateBatch.newBuilder()
                    .addReactionUpdates(reactionUpdate(MessagingModel.ReactionUpdate.Action.ADDED.number, 0.0, 0.0))
                    .addReactionUpdates(reactionUpdate(MessagingModel.ReactionUpdate.Action.BOOSTED.number, 2.5, 0.29))
                    .addReactionUpdates(reactionUpdate(7, 0.0, 0.0))
            )
        return EventModel.EventBatch.newBuilder()
            .addEvents(EventModel.Event.newBuilder().setChatUpdate(update))
            // A chat update with every batch absent.
            .addEvents(EventModel.Event.newBuilder().setChatUpdate(EventModel.ChatUpdate.newBuilder().setChat(chat)))
            // Not a chat update: Android hands blob updates elsewhere and drops test events.
            .addEvents(EventModel.Event.newBuilder().setBlobUpdate(EventModel.BlobUpdate.getDefaultInstance()))
            .addEvents(EventModel.Event.newBuilder().setTest(EventModel.TestEvent.getDefaultInstance()))
            .build()
    }

    /** What openStream's response handler does with [response], as values. */
    private fun describeStream(response: RpcEventStreamingService.StreamEventsResponse): JsonObject = buildJsonObject {
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

    // -- GetReactors responses --

    private fun getReactorsResponses(): JsonArray = buildJsonArray {
        fun entry(name: String, response: RpcMessagingService.GetReactorsResponse) = add(buildJsonObject {
            put("name", name)
            put("bytes", response.toByteArray().hex())
            put("expected", describeGetReactors(response))
        })
        val reactor = { user: ByteArray, boost: Double ->
            MessagingModel.Reactor.newBuilder().setUserId(userId(user)).setReactedTs(timestamp(1_762_000_100L, 7)).setVersion(2).setBoostUsd(boost)
        }
        val r = RpcMessagingService.GetReactorsResponse.newBuilder()
        entry("ok-page", r.clone()
            .setResult(RpcMessagingService.GetReactorsResponse.Result.OK)
            .addReactors(reactor(userA, 0.0)).addReactors(reactor(userB, 0.29))
            .setHasMore(true)
            .setPagingToken(Common.PagingToken.newBuilder().setValue(pagingToken.bs()))
            .build())
        entry("ok-last-page", r.clone().setResult(RpcMessagingService.GetReactorsResponse.Result.OK).build())
        listOf("denied" to 1, "message-not-found" to 2, "unknown-result" to 42).forEach { (name, value) ->
            entry(name, r.clone().setResultValue(value).build())
        }
    }

    /** What `ChatMessagingService.getReactors` returns for [response], as values. */
    private fun describeGetReactors(response: RpcMessagingService.GetReactorsResponse): JsonObject = buildJsonObject {
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

    // -- describing Android's domain values --

    private fun ChatUpdate.describe(): JsonObject = buildJsonObject {
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

    private fun ChatMessage.describe(): JsonObject = buildJsonObject {
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

    private fun EmojiReaction.describe(): JsonObject = buildJsonObject {
        put("emoji", emoji.value); put("count", count)
        selfReactor?.let { put("selfReactor", it.describe()) }
        put("sampleReactors", buildJsonArray { sampleReactors.forEach { add(it.describe()) } })
        put("version", version); put("boostCents", boostCents)
    }

    private fun Reactor.describe(): JsonObject = buildJsonObject {
        put("userId", userId.hex()); put("reactedAt", reactedAt.describe()); put("version", version); put("boostCents", boostCents)
    }

    // -- builders --

    private fun Instant.describe(): String = "$epochSeconds.${nanosecondsOfSecond.toString().padStart(9, '0')}"

    private fun timestamp(seconds: Long, nanos: Int): Timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build()

    private fun userId(bytes: ByteArray): Common.UserId = Common.UserId.newBuilder().setValue(bytes.bs()).build()

    private fun text(value: String): MessagingModel.Content =
        MessagingModel.Content.newBuilder().setText(MessagingModel.TextContent.newBuilder().setText(value)).build()

    private fun typing(state: Int) = MessagingModel.IsTypingNotification.newBuilder()
        .setUserId(userId(userB)).setStateValue(state)

    private fun pointer(type: Int, value: Long) = MessagingModel.Pointer.newBuilder()
        .setTypeValue(type).setUserId(userId(userB))
        .setValue(MessagingModel.MessageId.newBuilder().setValue(value))
        .setTs(timestamp(1_762_000_006L, 0))

    private fun reactionUpdate(action: Int, boost: Double, actorBoost: Double) = MessagingModel.ReactionUpdate.newBuilder()
        .setMessageId(MessagingModel.MessageId.newBuilder().setValue(42))
        .setEmoji(MessagingModel.Emoji.newBuilder().setValue("🔥"))
        .setActor(userId(userB)).setActionValue(action).setCount(2).setVersion(5)
        .setReactedTs(timestamp(1_762_000_007L, 0)).setBoostUsd(boost).setActorBoostUsd(actorBoost)
}
