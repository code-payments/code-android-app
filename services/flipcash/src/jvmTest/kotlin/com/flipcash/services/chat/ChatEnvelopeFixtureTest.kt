package com.flipcash.services.chat

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.api.ChatApi
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.asProtoKeyEnvelope
import com.flipcash.services.internal.network.extensions.toInviteKeyBundle
import com.flipcash.services.internal.network.extensions.toKeyEnvelope
import com.flipcash.services.internal.network.extensions.toMessageContent
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Records `chat_envelopes.json` for `:services:flipcash-chat`: the bytes Android's protobuf-java
 * code produces for message content, key envelopes and the Set/GetKeyEnvelope requests, plus
 * responses and the values Android decodes them to.
 *
 * Requests go through the real [ChatApi] (`authenticate()` over `buildPartial()`) into a channel
 * that records them and fails the call, so nothing leaves the process. Keys, chat ids and wrapped
 * keys come from chat-cipher's public vectors. Sealed content uses random nonces, so the sealed
 * vectors are recorded once and checked by opening them; everything else is deterministic.
 *
 * This test fails if Android's output drifts from the file; run with `-Dfixture.update=true` to
 * rewrite it. Opening a sealed vector is checked here too, so a rewrite keeps them valid.
 */
class ChatEnvelopeFixtureTest {

    private val vectors = File(requireNotNull(System.getProperty("chatCipherVectors")) { "chatCipherVectors not set" })
    private val chatCipher = json("chat_cipher.json")
    private val groupCipher = json("group_cipher.json")
    private val invite = json("invite.json")

    private val alice = chatCipher.list("keyConversion").first { it.str("name") == "alice" }
    private val bob = chatCipher.list("keyConversion").first { it.str("name") == "bob" }
    private val owner = Ed25519.createKeyPair(alice.str("edSeed").unhex())
    private val groupChatId = groupCipher.list("wraps").first().str("chatId").unhex()

    private val userA = ByteArray(16) { (0x10 + it).toByte() }
    private val userB = ByteArray(16) { (0x30 + it).toByte() }
    private val mint = ByteArray(32) { (0xC0 + it).toByte() }
    private val intentId = ByteArray(32) { (0x50 + it).toByte() }
    private val paymentRequestId = ByteArray(32) { (0x70 + it).toByte() }

    @Test
    fun androidOutputMatchesFixture() = runBlocking {
        val captured = buildJsonObject {
            put("note", "Recorded by ChatEnvelopeFixtureTest in :services:flipcash (jvmTest) from Android's protobuf-java code. Hex unless noted. Keys come from chat-cipher's public vectors. `sealed` vectors use random nonces and are checked by opening them.")
            put("ownerSeed", alice.str("edSeed"))
            put("contents", contents())
            put("sealed", sealed())
            put("keyEnvelopes", keyEnvelopes())
            put("requests", requests())
            put("getKeyEnvelopeResponses", getKeyEnvelopeResponses())
            put("setKeyEnvelopeResponses", setKeyEnvelopeResponses())
        }

        val file = File(requireNotNull(System.getProperty("chatEnvelopeFixture")) { "chatEnvelopeFixture not set" })
        if (System.getProperty("fixture.update") == "true") {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), captured) + "\n")
        }
        val pinned = Json.parseToJsonElement(file.readText()).jsonObject
        // Sealed vectors carry random nonces: pin the rest, and check the pinned sealed ones open.
        assertEquals(JsonObject(pinned - "sealed"), JsonObject(captured - "sealed"))
        checkSealedOpen(pinned.getValue("sealed").jsonArray)
    }

    // -- content --

    private fun contents(): JsonArray = buildJsonArray {
        // Encoded by Android's asContent(); the Wire codec must produce the same bytes.
        fun encoded(name: String, content: MessageContent) = add(vector(name, "encode", content.asContent()))
        // Built with protobuf-java builders (server-authored or decode-only); the Wire codec must decode them alike.
        fun decoded(name: String, content: MessagingModel.Content) = add(vector(name, "decode", content))

        encoded("text", MessageContent.Text("hello"))
        encoded("text-unicode", MessageContent.Text("gm ☀️ 💸 café"))
        encoded("text-empty", MessageContent.Text(""))
        encoded(
            "text-link-preview",
            MessageContent.Text(
                "see https://example.com/a",
                linkPreview = com.flipcash.services.models.chat.LinkPreview(
                    url = "https://example.com/a",
                    title = "Example",
                    description = "An example page",
                ),
            ),
        )
        encoded("reply-of-text", MessageContent.Reply(42L, listOf(MessageContent.Text("yes"))))
        encoded(
            "cash-single",
            MessageContent.Cash(
                intentId = intentId.toList(),
                amount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.USD),
                mint = Mint(mint.toList()),
                receiver = userB.toList(),
                tokenQuarks = 1_500_000L,
                paymentRequestId = paymentRequestId.toList(),
            ),
        )
        encoded(
            "cash-tipped-multi",
            MessageContent.Cash(
                intentId = intentId.toList(),
                amount = Fiat(fiat = 2.0, currencyCode = CurrencyCode.USD),
                mint = Mint(mint.toList()),
                action = MessageContent.Cash.Action.TIPPED,
                receiver = userA.toList(),
                tokenQuarks = 2_000_000L,
                multiReceiver = listOf(
                    MessageContent.Cash.Payment(userA.toList(), 1.25, 1_250_000L),
                    MessageContent.Cash.Payment(userB.toList(), 0.75, 750_000L),
                ),
            ),
        )
        encoded(
            "reply-of-cash",
            MessageContent.Reply(
                43L,
                listOf(
                    MessageContent.Cash(
                        intentId = intentId.toList(),
                        amount = Fiat(fiat = 0.25, currencyCode = CurrencyCode.USD),
                        mint = Mint(mint.toList()),
                        receiver = userA.toList(),
                        tokenQuarks = 250_000L,
                    ),
                ),
            ),
        )
        encoded("widget-payment-request", MessageContent.Widget(WidgetContent.PaymentRequest(paymentRequestId.toList())))
        encoded("system-fallback", MessageContent.System("Something happened"))
        encoded(
            "system-joined-admitted",
            MessageContent.System("Bob joined", MessageContent.SystemEvent.UserJoined(userB.toList(), admittedBy = userA.toList())),
        )
        encoded(
            "system-joined-invited",
            MessageContent.System("Bob joined", MessageContent.SystemEvent.UserJoined(userB.toList(), admittedBy = null, invitedBy = userA.toList())),
        )
        encoded("system-joined-direct", MessageContent.System("Bob joined", MessageContent.SystemEvent.UserJoined(userB.toList(), null)))
        encoded("deleted", MessageContent.Deleted(Instant.fromEpochSeconds(1_700_000_000L, 123_000_000), userA.toList()))
        encoded("deleted-no-actor", MessageContent.Deleted(Instant.fromEpochSeconds(1_700_000_000L, 0), null))
        encoded("encrypted-group", MessageContent.Encrypted(2, ByteArray(24) { it.toByte() }, ByteArray(40) { (0xA0 + it).toByte() }))

        val cash = MessagingModel.CashContent.newBuilder()
            .setIntentId(Common.IntentId.newBuilder().setValue(intentId.bs()))
            .setTotalAmount(
                Common.CryptoPaymentAmount.newBuilder().setCurrency("CAD").setNativeAmount(12.25).setQuarks(12_250_000L)
                    .setMint(Common.PublicKey.newBuilder().setValue(mint.bs())),
            )
            .setSingleReceiver(Common.UserId.newBuilder().setValue(userB.bs()))
        decoded(
            "cash-return-offer",
            content { setCash(cash.clone().setPaymentReturn(MessagingModel.CashContent.Return.newBuilder().setOffer(MessagingModel.CashContent.Return.Offer.getDefaultInstance()))) },
        )
        decoded(
            "cash-return-of",
            content {
                setCash(
                    cash.clone().setVerbValue(77).setPaymentReturn(
                        MessagingModel.CashContent.Return.newBuilder().setReturnOf(
                            MessagingModel.CashContent.Return.ReturnOf.newBuilder().setMessageId(MessagingModel.MessageId.newBuilder().setValue(9L)),
                        ),
                    ),
                )
            },
        )
        decoded(
            "widget-share-profile",
            content { setWidget(MessagingModel.WidgetContent.newBuilder().setShareProfile(MessagingModel.ShareProfileWidget.newBuilder().setUsername(Common.Username.newBuilder().setValue("alice")))) },
        )
        decoded("widget-unknown", content { setWidget(MessagingModel.WidgetContent.getDefaultInstance()) })
        decoded(
            "encrypted-unknown-scheme",
            content { setEncrypted(MessagingModel.EncryptedContent.newBuilder().setSchemeValue(7).setNonce(ByteArray(24).bs()).setCiphertext(ByteArray(17).bs())) },
        )
        decoded(
            "system-joined-no-user",
            content { setSystem(MessagingModel.SystemContent.newBuilder().setFallbackText("joined").setUserJoined(MessagingModel.SystemContent.UserJoined.getDefaultInstance())) },
        )
        decoded("empty-content", MessagingModel.Content.getDefaultInstance())
        // Media is not ported: Android decodes it, :services:flipcash-chat reads it as unsupported.
        decoded("media-unported", content { setMedia(MessagingModel.MediaContent.getDefaultInstance()) })
        // A preview's image is media too: the KMP decode keeps the text and preview and drops the image.
        decoded(
            "text-link-preview-image",
            content {
                setText(
                    MessagingModel.TextContent.newBuilder()
                        .setText("see https://example.com/b")
                        .addLinkPreviews(
                            MessagingModel.LinkPreview.newBuilder()
                                .setUrl("https://example.com/b")
                                .setTitle("Example B")
                                .setImage(com.codeinc.flipcash.gen.blob.v1.Model.Media.getDefaultInstance()),
                        ),
                )
            },
        )
    }

    private fun vector(name: String, direction: String, content: MessagingModel.Content) = buildJsonObject {
        put("name", name)
        put("direction", direction)
        put("bytes", content.toByteArray().hex())
        put("decoded", describe(content.toMessageContent()))
    }

    private fun content(block: MessagingModel.Content.Builder.() -> Unit): MessagingModel.Content =
        MessagingModel.Content.newBuilder().apply(block).build()

    /** Android's decoded value, in the shape `:services:flipcash-chat`'s test describes its own. */
    private fun describe(content: MessageContent): JsonObject = buildJsonObject {
        when (content) {
            is MessageContent.Text -> {
                put("type", "text"); put("text", content.text)
                content.linkPreview?.let { p ->
                    put("linkPreview", buildJsonObject { put("url", p.url); put("title", p.title); put("description", p.description) })
                }
            }
            is MessageContent.Cash -> {
                put("type", "cash")
                put("intentId", content.intentId.hex())
                put("mint", content.mint.bytes.hex())
                put("quarks", content.tokenQuarks ?: content.amount.quarks)
                put("verb", content.action.name)
                content.receiver?.let { put("receiver", it.hex()) }
                put("multiReceiver", buildJsonArray {
                    content.multiReceiver.forEach { p ->
                        add(buildJsonObject { put("receiver", p.receiver.hex()); put("nativeAmount", p.nativeAmount); put("quarks", p.tokenQuarks) })
                    }
                })
                content.paymentRequestId?.let { put("paymentRequestId", it.hex()) }
                content.paymentReturn?.let { r ->
                    put("paymentReturn", buildJsonObject {
                        put("offer", r.offer != null)
                        r.returnOf?.let { put("returnOf", it.messageId) }
                    })
                }
            }
            is MessageContent.Reply -> {
                put("type", "reply")
                put("repliedMessageId", content.repliedMessageId)
                put("content", buildJsonArray { content.content.forEach { add(describe(it)) } })
            }
            is MessageContent.Widget -> when (val w = content.widget) {
                is WidgetContent.PaymentRequest -> { put("type", "widget-payment-request"); put("paymentRequestId", w.id.hex()) }
                is WidgetContent.ShareProfile -> { put("type", "widget-share-profile"); put("username", w.username) }
                WidgetContent.Unsupported -> put("type", "widget-unsupported")
            }
            is MessageContent.System -> {
                put("type", "system")
                put("fallbackText", content.fallbackText)
                (content.event as? MessageContent.SystemEvent.UserJoined)?.let { j ->
                    put("userJoined", buildJsonObject {
                        put("userId", j.userId.hex())
                        j.admittedBy?.let { put("admittedBy", it.hex()) }
                        j.invitedBy?.let { put("invitedBy", it.hex()) }
                    })
                }
            }
            is MessageContent.Deleted -> {
                put("type", "deleted")
                put("seconds", content.deletedTs.epochSeconds)
                put("nanos", content.deletedTs.nanosecondsOfSecond)
                content.deletedBy?.let { put("deletedBy", it.hex()) }
            }
            is MessageContent.Encrypted -> {
                put("type", "encrypted"); put("scheme", content.scheme)
                put("nonce", content.nonce.hex()); put("ciphertext", content.ciphertext.hex())
            }
            is MessageContent.Media -> put("type", "media")
            MessageContent.Unsupported -> put("type", "unsupported")
        }
    }

    // -- sealed content --

    private fun sealed(): JsonArray = buildJsonArray {
        val dm = chatCipher.list("chatKey").first()
        val chatKey = dm.str("chatKey").unhex()
        val dmChatId = dm.str("chatId").unhex()
        val alicePk = alice.str("edPublicKey").unhex()
        val bobPk = bob.str("edPublicKey").unhex()
        fun dmVector(name: String, content: MessageContent) {
            val payload = DefaultChatCipher.encrypt(content.asContent().toByteArray(), chatKey, alicePk, bobPk, dmChatId)
            add(buildJsonObject {
                put("name", name); put("kind", "dm")
                put("chatId", dmChatId.hex()); put("chatKey", chatKey.hex())
                put("senderPublicKey", alicePk.hex()); put("recipientPublicKey", bobPk.hex())
                put("bytes", MessageContent.Encrypted(1, payload.nonce, payload.ciphertext).asContent().toByteArray().hex())
                put("opened", describe(content))
            })
        }
        val wrap = groupCipher.list("wraps").first()
        val groupKey = wrap.str("groupKey").unhex()
        fun groupVector(name: String, content: MessageContent) {
            val payload = DefaultChatCipher.encryptGroup(content.asContent().toByteArray(), groupKey, groupChatId, userA)
            add(buildJsonObject {
                put("name", name); put("kind", "group")
                put("chatId", groupChatId.hex()); put("groupKey", groupKey.hex()); put("senderId", userA.hex())
                put("bytes", MessageContent.Encrypted(2, payload.nonce, payload.ciphertext).asContent().toByteArray().hex())
                put("opened", describe(content))
            })
        }
        dmVector("dm-text", MessageContent.Text("sealed hello"))
        dmVector("dm-reply", MessageContent.Reply(7L, listOf(MessageContent.Text("sealed reply"))))
        groupVector("group-text", MessageContent.Text("group hello"))
        groupVector("group-payment-request", MessageContent.Widget(WidgetContent.PaymentRequest(paymentRequestId.toList())))
    }

    private fun checkSealedOpen(sealed: JsonArray) {
        sealed.forEach { element ->
            val v = element.jsonObject
            val encrypted = MessagingModel.Content.parseFrom(v.str("bytes").unhex()).encrypted
            val payload = com.getcode.chatcipher.EncryptedPayload(encrypted.nonce.toByteArray(), encrypted.ciphertext.toByteArray())
            val plaintext = when (v.str("kind")) {
                "dm" -> DefaultChatCipher.decrypt(payload, v.str("chatKey").unhex(), v.str("senderPublicKey").unhex(), v.str("recipientPublicKey").unhex(), v.str("chatId").unhex())
                else -> DefaultChatCipher.decryptGroup(payload, v.str("groupKey").unhex(), v.str("chatId").unhex(), v.str("senderId").unhex())
            }
            assertEquals(v.getValue("opened"), describe(MessagingModel.Content.parseFrom(plaintext).toMessageContent()), v.str("name"))
        }
    }

    // -- key envelopes --

    private fun keyEnvelopes(): JsonArray = buildJsonArray {
        groupCipher.list("wraps").forEach { w ->
            val envelope = KeyEnvelope(KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305, w.str("nonce").unhex(), w.str("ciphertext").unhex())
            add(buildJsonObject {
                put("name", w.str("name"))
                put("bytes", envelope.asProtoKeyEnvelope().toByteArray().hex())
                put("scheme", envelope.scheme); put("nonce", w.str("nonce")); put("ciphertext", w.str("ciphertext"))
                put("wrapperPublicKey", w.str("wrapperPublicKey")); put("recipientSeed", w.str("recipientSeed"))
                put("chatId", w.str("chatId")); put("groupKey", w.str("groupKey"))
            })
        }
        val unknown = ChatModel.KeyEnvelope.newBuilder().setSchemeValue(5).setNonce(ByteArray(24).bs()).setCiphertext(ByteArray(48).bs()).build()
        add(buildJsonObject {
            put("name", "unknown-scheme")
            put("bytes", unknown.toByteArray().hex())
            val decoded = unknown.toKeyEnvelope()
            put("scheme", decoded.scheme); put("nonce", decoded.nonce.hex()); put("ciphertext", decoded.ciphertext.hex())
        })
    }

    private suspend fun requests(): JsonObject {
        val channel = CapturingChannel()
        val api = ChatApi(channel)
        val chatId = ChatId(groupChatId)
        val w = groupCipher.list("wraps").first()
        val envelope = KeyEnvelope(KeyEnvelope.SCHEME_X25519_XCHACHA20POLY1305, w.str("nonce").unhex(), w.str("ciphertext").unhex())

        runCatching { api.getKeyEnvelope(owner, chatId) }
        val get = RpcChatService.GetKeyEnvelopeRequest.parseFrom(channel.takeOnly())
        // SetKeyEnvelope mutates: the channel captures the request and fails the call, so it is never sent.
        runCatching { api.setKeyEnvelope(owner, chatId, envelope) }
        val set = RpcChatService.SetKeyEnvelopeRequest.parseFrom(channel.takeOnly())

        return buildJsonObject {
            put("chatId", groupChatId.hex())
            put("getKeyEnvelope", get.toByteArray().hex())
            put("setKeyEnvelope", buildJsonObject {
                put("envelope", w.str("name"))
                put("request", set.toByteArray().hex())
            })
        }
    }

    private fun getKeyEnvelopeResponses(): JsonArray = buildJsonArray {
        val w = groupCipher.list("wraps").first()
        val own = ChatModel.KeyEnvelope.newBuilder().setSchemeValue(1).setNonce(w.str("nonce").unhex().bs()).setCiphertext(w.str("ciphertext").unhex().bs())
        val wrappedBy = Common.UserId.newBuilder().setValue(userA.bs())
        val wrappedByPk = Common.PublicKey.newBuilder().setValue(w.str("wrapperPublicKey").unhex().bs())
        val b = invite.list("bundles").first()
        val bundle = RpcChatService.GetKeyEnvelopeResponse.InviteKeyBundle.newBuilder()
            .setInviteId(ChatModel.InviteId.newBuilder().setValue(b.str("inviteId").unhex().bs()))
            .setKeyEnvelope(
                ChatModel.InviteKeyEnvelope.newBuilder().setSchemeValue(1)
                    .setNonce(b.obj("keyEnvelope").str("nonce").unhex().bs())
                    .setCiphertext(b.obj("keyEnvelope").str("ciphertext").unhex().bs())
                    .setSignature(b.obj("keyEnvelope").str("signature").unhex().bs()),
            )
            .setSecretEnvelope(
                ChatModel.InviteSecretEnvelope.newBuilder().setSchemeValue(1)
                    .setNonce(b.obj("secretEnvelope").str("nonce").unhex().bs())
                    .setCiphertext(b.obj("secretEnvelope").str("ciphertext").unhex().bs()),
            )
        fun response(name: String, block: RpcChatService.GetKeyEnvelopeResponse.Builder.() -> Unit) {
            val response = RpcChatService.GetKeyEnvelopeResponse.newBuilder().apply(block).build()
            add(buildJsonObject {
                put("name", name)
                put("bytes", response.toByteArray().hex())
                put("decoded", describeLookup(response))
            })
        }
        response("own") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.OK); setKeyEnvelope(own); setWrappedBy(wrappedBy); setWrappedByPublicKey(wrappedByPk) }
        response("own-no-wrapper") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.OK); setKeyEnvelope(own) }
        response("own-unknown-scheme") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.OK); setKeyEnvelope(own.clone().setSchemeValue(9)) }
        response("invite-only") {
            setResult(RpcChatService.GetKeyEnvelopeResponse.Result.OK); setInviteKeyBundle(bundle)
            setWrappedByPublicKey(Common.PublicKey.newBuilder().setValue(b.str("wrappedByPublicKey").unhex().bs()))
        }
        response("ok-empty") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.OK) }
        response("no-envelope") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.NO_ENVELOPE) }
        response("denied") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.DENIED) }
        response("not-found") { setResult(RpcChatService.GetKeyEnvelopeResponse.Result.NOT_FOUND) }
        response("unknown-result") { setResultValue(42) }
    }

    /** Android's reading: ChatService's result switch, then InternalChatRepository's envelope case. */
    private fun describeLookup(response: RpcChatService.GetKeyEnvelopeResponse): JsonObject = buildJsonObject {
        when (response.result) {
            RpcChatService.GetKeyEnvelopeResponse.Result.DENIED -> put("type", "denied")
            RpcChatService.GetKeyEnvelopeResponse.Result.NOT_FOUND -> put("type", "not-found")
            RpcChatService.GetKeyEnvelopeResponse.Result.OK -> when (response.envelopeCase) {
                RpcChatService.GetKeyEnvelopeResponse.EnvelopeCase.KEY_ENVELOPE -> {
                    put("type", "own")
                    val e = response.keyEnvelope.toKeyEnvelope()
                    put("scheme", e.scheme); put("nonce", e.nonce.hex()); put("ciphertext", e.ciphertext.hex())
                    wrappers(response)
                }
                RpcChatService.GetKeyEnvelopeResponse.EnvelopeCase.INVITE_KEY_BUNDLE -> {
                    put("type", "invite-only")
                    val bundle = response.toInviteKeyBundle()
                    put("inviteId", bundle.inviteId.bytes.hex())
                    put("keyEnvelopeScheme", bundle.keyEnvelope.scheme)
                    put("keyNonce", bundle.keyEnvelope.nonce.hex()); put("keyCiphertext", bundle.keyEnvelope.ciphertext.hex())
                    put("keySignature", bundle.keyEnvelope.signature.hex())
                    put("secretEnvelopeScheme", bundle.secretEnvelope.scheme)
                    put("secretNonce", bundle.secretEnvelope.nonce.hex()); put("secretCiphertext", bundle.secretEnvelope.ciphertext.hex())
                    wrappers(response)
                }
                else -> put("type", "no-envelope")
            }
            // NO_ENVELOPE, and a result this build does not know.
            else -> put("type", "no-envelope")
        }
    }

    private fun JsonObjectBuilder.wrappers(response: RpcChatService.GetKeyEnvelopeResponse) {
        if (response.hasWrappedBy()) put("wrappedBy", response.wrappedBy.value.toByteArray().hex())
        if (response.hasWrappedByPublicKey()) put("wrappedByPublicKey", response.wrappedByPublicKey.value.toByteArray().hex())
    }

    private fun setKeyEnvelopeResponses(): JsonArray = buildJsonArray {
        listOf("ok" to 0, "denied" to 1, "not-found" to 2, "already-set" to 3, "unknown-result" to 42).forEach { (name, value) ->
            val response = RpcChatService.SetKeyEnvelopeResponse.newBuilder().setResultValue(value).build()
            add(buildJsonObject {
                put("name", name)
                put("bytes", response.toByteArray().hex())
                put("result", response.result.name)
            })
        }
    }

    // -- helpers --

    private fun json(name: String): JsonObject = Json.parseToJsonElement(File(vectors, name).readText()).jsonObject
}
